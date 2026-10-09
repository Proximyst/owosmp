package dev.mardroemmar.owosmp.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonNumber;
import com.openai.core.JsonString;
import com.openai.core.RequestOptions;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionCreateParams.Verbosity;
import com.openai.models.completions.CompletionUsage;
import dev.mardroemmar.owosmp.config.LlmConfig;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import java.io.Closeable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LlmSession implements Closeable {
  private static final @NotNull Logger LOGGER = LoggerFactory.getLogger(LlmSession.class);

  /** Pattern that matches emojis and keycap sequences. */
  private static final @NotNull Pattern EMOJIS_PATTERN =
      Pattern.compile(
          "[#*0-9]\\uFE0F?\\u20E3|\\u200D?[\\p{IsEmoji}&&[^#*0-9]][\\uFE0E\\uFE0F\\x{E0020}-\\x{E007F}]*");

  private final @NotNull ModelPricing modelPricing = new ModelPricing();
  private final @NotNull ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final @NotNull List<ChatEvent> chatEvents = new CopyOnWriteArrayList<>();
  private final @NotNull OpenAIClient client;

  private volatile @NotNull LlmConfig config;
  private volatile boolean closed;

  public LlmSession(@NotNull LlmConfig config) {
    this.config = config;

    String endpoint = config.endpoint();
    while (endpoint.endsWith("/")) {
      endpoint = endpoint.substring(0, endpoint.length() - 1);
    }
    if (endpoint.endsWith("/chat/completions")) {
      endpoint = endpoint.substring(0, endpoint.length() - "/chat/completions".length());
    }
    this.client =
        OpenAIOkHttpClient.builder().apiKey(config.bearerToken()).baseUrl(endpoint).build();
  }

  /* package-private */ void handleChatMessage(
      @NotNull ServerLevel level, @NotNull ServerPlayer player, @NotNull String message) {
    if (this.closed) {
      LOGGER
          .atWarn()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("LLM session is closed, yet chat message was received");
      return;
    }
    if (!permitInteraction(level, player)) {
      LOGGER
          .atDebug()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("Ignoring LLM interaction in chat");
      return;
    }

    var prompt = parsePrompt(message);
    if (prompt == null) {
      if (level.getGameRules().get(GameRules.LLM_INCLUDE_PLAYER_CHAT)) {
        this.chatEvents.add(new ChatEvent(stripEmojis(message), ChatRole.USER));
      } else {
        LOGGER
            .atDebug()
            .addKeyValue("player", player.getPlainTextName())
            .addKeyValue("world", level.dimension().identifier())
            .log("Ignoring LLM interaction for chat message due to game rule");
      }
      return;
    }

    boolean includePrompt = level.getGameRules().get(GameRules.LLM_INCLUDE_SYSTEM_PROMPT);
    var reasoningEffort =
        mapKnownReasoningEffort(level.getGameRules().get(GameRules.LLM_REASONING_EFFORT));
    var verbosity = mapKnownVerbosity(level.getGameRules().get(GameRules.LLM_VERBOSITY));

    this.executor.execute(
        () -> {
          final var reply =
              complete(
                  prompt, player.getName().getString(), includePrompt, reasoningEffort, verbosity);
          LOGGER
              .atDebug()
              .addKeyValue("player", player.getPlainTextName())
              .addKeyValue("world", level.dimension().identifier())
              .addKeyValue("prompt", prompt.text())
              .addKeyValue("reply", reply.text())
              .addKeyValue("success", reply.success())
              .addKeyValue(
                  "costEstimate",
                  reply.costEstimate() == null ? "unknown" : formatCost(reply.costEstimate()))
              .addKeyValue("duration", formatDuration(reply.duration()))
              .log("LLM chat completion finished");
          recordChatEvents(
              List.of(
                  new ChatEvent(
                      formatChatEvent(player.getName().getString(), stripEmojis(message)),
                      ChatRole.USER),
                  new ChatEvent(reply.text(), ChatRole.ASSISTANT)),
              level);
          level.getServer().execute(() -> broadcast(level, reply));
        });
  }

  /* package-private */ void handlePlayerConnection(
      @NotNull ServerLevel level, @NotNull ServerPlayer player, boolean connected) {
    if (this.closed) {
      LOGGER
          .atWarn()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("LLM session is closed, yet player connection event was received");
      return;
    }
    if (!permitInteraction(level, player)) {
      LOGGER
          .atDebug()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("Ignoring LLM interaction for player connection event");
      return;
    }
    if (!level.getGameRules().get(GameRules.LLM_INCLUDE_PLAYER_CONNECTIONS)) {
      LOGGER
          .atDebug()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("Ignoring LLM interaction for player connection event due to game rule");
      return;
    }

    var message =
        connected
            ? formatChatEvent(player.getName().getString(), "has joined the game")
            : formatChatEvent(player.getName().getString(), "has left the game");
    LOGGER.atDebug().addKeyValue("message", message).log("Player connection event sent to LLM");
    this.chatEvents.add(new ChatEvent(stripEmojis(message), ChatRole.SYSTEM));
  }

  /* package-private */ void handlePlayerDeath(
      @NotNull ServerLevel level, @NotNull ServerPlayer player, @NotNull String deathMessage) {
    if (this.closed) {
      LOGGER
          .atWarn()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("LLM session is closed, yet player death event was received");
      return;
    }
    if (!permitInteraction(level, player)) {
      LOGGER
          .atDebug()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("Ignoring LLM interaction for player death event");
      return;
    }
    if (!level.getGameRules().get(GameRules.LLM_INCLUDE_PLAYER_DEATHS)) {
      LOGGER
          .atDebug()
          .addKeyValue("player", player.getPlainTextName())
          .addKeyValue("world", level.dimension().identifier())
          .log("Ignoring LLM interaction for player death event due to game rule");
      return;
    }

    LOGGER
        .atInfo()
        .addKeyValue("player", player.getPlainTextName())
        .addKeyValue("world", level.dimension().identifier())
        .addKeyValue("deathMessage", deathMessage)
        .log("Player death event sent to LLM");
    var message = formatChatEvent(player.getName().getString(), deathMessage);
    this.chatEvents.add(new ChatEvent(stripEmojis(message), ChatRole.SYSTEM));
  }

  private void broadcast(@NotNull ServerLevel level, @NotNull Reply reply) {
    var costEstimate = reply.costEstimate();
    var formattedCost = costEstimate == null ? "unknown" : formatCost(costEstimate);
    var hover =
        Component.literal(
            "Cost: "
                + formattedCost
                + "\nDuration: "
                + formatDuration(reply.duration())
                + "\nModel: "
                + reply.model());
    var message =
        Component.empty()
            .withStyle(style -> style.withHoverEvent(new ShowText(hover)))
            .append(chatPrefix())
            .append(reply.text());
    level.getServer().getPlayerList().broadcastSystemMessage(message, false);
  }

  private @NotNull String formatChatEvent(@NotNull String speaker, @NotNull String message) {
    return "<" + speaker + "> " + message;
  }

  /** Produce a text completion based on the history and input from the user. */
  private @NotNull Reply complete(
      @NotNull Prompt prompt,
      @NotNull String speaker,
      boolean includePrompt,
      @NotNull ReasoningEffort reasoningEffort,
      @NotNull Verbosity verbosity) {
    final var userText = formatChatEvent(speaker, prompt.text());
    final var history = List.copyOf(this.chatEvents);
    final var params =
        ChatCompletionCreateParams.builder()
            .model(prompt.model())
            .verbosity(verbosity)
            .reasoningEffort(reasoningEffort);
    if (includePrompt) {
      params.addSystemMessage(config.systemPrompt());
    }
    for (var event : history) {
      switch (event.role()) {
        case SYSTEM -> params.addSystemMessage(event.contents());
        case USER -> params.addUserMessage(event.contents());
        case ASSISTANT -> params.addAssistantMessage(event.contents());
      }
    }
    params.addUserMessage(userText);

    final var started = System.nanoTime();
    final ChatCompletion completion;
    try {
      completion =
          client
              .chat()
              .completions()
              .create(
                  params.build(),
                  RequestOptions.builder().timeout(config.completionTimeout()).build());
    } catch (Exception ex) {
      LOGGER
          .atError()
          .addKeyValue("model", prompt.model())
          .addKeyValue("speaker", speaker)
          .addKeyValue("userText", userText)
          .setCause(ex)
          .log("Failed to complete LLM prompt");
      return new Reply(
          false,
          "API request error, see console",
          prompt.model(),
          null,
          Duration.ofNanos(System.nanoTime() - started));
    }
    final var latency = Duration.ofNanos(System.nanoTime() - started);

    var rawReply =
        completion.choices().stream()
            .findFirst()
            .flatMap(choice -> choice.message().content())
            .orElse("I don't have a reply for that.");
    var cleanedReply =
        stripEmojis(rawReply)
            .lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .collect(Collectors.joining("\n", "", ""));
    var indicatedCost = parseCostFromReply(completion);
    var inputTokens =
        completion
            .usage()
            .map(CompletionUsage::promptTokens)
            .orElseGet(() -> estimateTokens(userText));
    var outputTokens =
        completion
            .usage()
            .map(CompletionUsage::completionTokens)
            .orElseGet(() -> estimateTokens(rawReply));
    var costEstimate =
        indicatedCost != null
            ? indicatedCost
            : modelPricing.estimatePrice(prompt.model(), inputTokens, outputTokens);
    return new Reply(true, cleanedReply, prompt.model(), costEstimate, latency);
  }

  /** Read the indicated cost from the completion reply, if present. */
  private @Nullable BigDecimal parseCostFromReply(@NotNull ChatCompletion completion) {
    return completion
        .usage()
        .map(usage -> usage._additionalProperties().get("cost"))
        .map(
            cost -> {
              if (cost instanceof JsonNumber number) {
                if (number.value() instanceof BigDecimal b) {
                  return b;
                }
                try {
                  return new BigDecimal(number.toString());
                } catch (NumberFormatException e) {
                  return null;
                }
              } else if (cost instanceof JsonString string) {
                try {
                  return new BigDecimal(string.value());
                } catch (NumberFormatException e) {
                  return null;
                }
              }
              return null;
            })
        .orElse(null);
  }

  @Override
  public void close() {
    this.closed = true;
    this.executor.shutdownNow();
  }

  public void clearHistory() {
    this.chatEvents.clear();
  }

  public void updateConfig(@NotNull LlmConfig config) {
    this.config = config;
  }

  /** How many messages in the chat history should be piped to the LLM's conversation? */
  private int maxChatHistory(final @NotNull ServerLevel level) {
    return level.getGameRules().get(GameRules.LLM_HISTORY);
  }

  /** The prefix to prepend to messages sent to the game chat. */
  private @NotNull Component chatPrefix() {
    return Component.empty()
        .append("<")
        .append(Component.literal("AI").withStyle(style -> style.withColor(0x4A6C6F)))
        .append("> ");
  }

  /** Figure out a player from their name. */
  private @Nullable ServerPlayer derivePlayer(
      @NotNull ServerLevel level, @NotNull String playerName) {
    return level.getServer().getPlayerList().getPlayerByName(playerName);
  }

  /** Check whether we should permit interaction with the LLM. */
  private boolean permitInteraction(@NotNull ServerLevel level, @NotNull ServerPlayer player) {
    return !closed
        && !level.getGameRules().get(GameRules.DISABLE_LLM)
        && !config.bannedPlayers().contains(player.getUUID());
  }

  /** Remove emojis and other non-renderable sequences from the given text. */
  private @NotNull String stripEmojis(@NotNull String text) {
    return EMOJIS_PATTERN.matcher(text).replaceAll("");
  }

  /** Rough estimate of LLM tokens in a string. */
  private long estimateTokens(@NotNull String text) {
    return (((long) text.codePointCount(0, text.length())) + 3L) / 4L;
  }

  /** Format the cost of an invocation to a human-readable string, assuming a dollar value. */
  private @NotNull String formatCost(@NotNull BigDecimal usd) {
    var scaled = usd.scale() > 8 ? usd.setScale(8, RoundingMode.HALF_UP) : usd;
    var plain = scaled.stripTrailingZeros().toPlainString();
    return "$" + plain;
  }

  /** Format a duration into a human-readable string, in the format {@code ss.SSS"s"}. */
  private @NotNull String formatDuration(@NotNull Duration duration) {
    var seconds = duration.getSeconds();
    var absSeconds = Math.abs(seconds);
    var positive = String.format("%d.%03ds", absSeconds, duration.toMillisPart());
    return seconds < 0 ? "-" + positive : positive;
  }

  /** Parse a chat message into a prompt. */
  private @Nullable Prompt parsePrompt(@NotNull String message) {
    message = message.trim();
    var lower = message.toLowerCase(Locale.ROOT);
    String model;
    for (var kv : config.triggers().entrySet()) {
      var trigger = kv.getKey().toLowerCase(Locale.ROOT);
      // We need to ensure we match `${key} `, `${key}, `, or `${key}: ` (with spaces).
      if (lower.length() <= trigger.length() + 1) {
        // Too short or the trigger _is_ the message, so we can't match it.
        continue;
      }
      if (!lower.startsWith(trigger)) {
        // The message doesn't start with any part of the trigger.
        continue;
      }
      // We now need to ensure we follow with a space, comma+space, or colon+space.
      switch (lower.charAt(trigger.length())) {
        case ' ':
          break;
        case ',':
        case ':':
          if (lower.length() <= trigger.length() + 1) {
            // Too short to have a space after the comma or colon.
            continue;
          }
          var nextChar = lower.charAt(trigger.length() + 1);
          if (nextChar != ' ') {
            // The trigger is not followed by a space.
            continue;
          }
          break;
        default:
          continue;
      }
      var nextChar = lower.charAt(trigger.length());
      if (nextChar != ' ' && nextChar != ',' && nextChar != ':') {
        // The trigger is not followed by a space, comma, or colon.
        continue;
      }

      model = kv.getValue();
      // We do +1 to skip the space, comma, or colon after the trigger.
      // The spaces after the comma/colon are trimmed with the `trim` call.
      message = message.substring(trigger.length() + 1).trim();
      return new Prompt(model, message);
    }
    return null;
  }

  /**
   * Record a chat event in the session's history, trimming the history if it exceeds the maximum
   * number of turns. The message is stripped of emojis before being recorded.
   */
  private void recordChatEvents(
      @NotNull List<@NotNull ChatEvent> events, @NotNull ServerLevel level) {
    while (!this.chatEvents.isEmpty() && this.chatEvents.size() >= maxChatHistory(level)) {
      this.chatEvents.removeFirst();
    }
    if (maxChatHistory(level) != 0) {
      this.chatEvents.addAll(events);
    }
  }

  /** Convert a known reasoning effort to an API-compatible one. */
  private @NotNull ReasoningEffort mapKnownReasoningEffort(ReasoningEffort.@NotNull Known known) {
    return switch (known) {
      case NONE -> ReasoningEffort.NONE;
      case MINIMAL -> ReasoningEffort.MINIMAL;
      case LOW -> ReasoningEffort.LOW;
      case MEDIUM -> ReasoningEffort.MEDIUM;
      case HIGH -> ReasoningEffort.HIGH;
      case XHIGH -> ReasoningEffort.XHIGH;
      case MAX -> ReasoningEffort.MAX;
    };
  }

  /** Convert a known verbosity to an API-compatible one. */
  private @NotNull Verbosity mapKnownVerbosity(Verbosity.@NotNull Known known) {
    return switch (known) {
      case LOW -> Verbosity.LOW;
      case MEDIUM -> Verbosity.MEDIUM;
      case HIGH -> Verbosity.HIGH;
    };
  }

  /** Where'd the message come from? */
  private enum ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
  }

  /** A single event to relay to the LLM chat. */
  private record ChatEvent(@NotNull String contents, @NotNull ChatRole role) {}

  /** A prompt to send to the LLM. */
  private record Prompt(@NotNull String model, @NotNull String text) {}

  /** A reply from the LLM. */
  private record Reply(
      boolean success,
      @NotNull String text,
      @NotNull String model,
      @Nullable BigDecimal costEstimate,
      @NotNull Duration duration) {}
}
