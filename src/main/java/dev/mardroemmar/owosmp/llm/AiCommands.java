package dev.mardroemmar.owosmp.llm;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.mardroemmar.owosmp.GsonHolder;
import dev.mardroemmar.owosmp.config.ConfigHelper;
import dev.mardroemmar.owosmp.config.ImmutableLlmConfig;
import dev.mardroemmar.owosmp.config.ImmutableOwoSmpConfig;
import dev.mardroemmar.owosmp.config.LlmConfig;
import dev.mardroemmar.owosmp.immutable.KeepAnnotations;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.GameProfileArgument.Result;
import net.minecraft.network.chat.Component;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KeepAnnotations
@Gson.TypeAdapters
@Value.Immutable
interface KeyStatusResponse {
  @Nullable
  StatusData data();

  @Nullable
  @SerializedName("limit_remaining")
  BigDecimal limitRemaining();

  @Nullable
  BigDecimal limit();

  @Nullable
  BigDecimal usage();

  @Nullable
  @SerializedName("usage_monthly")
  BigDecimal usageMonthly();

  @Nullable
  @SerializedName("usage_daily")
  BigDecimal usageDaily();

  @Nullable
  String label();

  @Value.Immutable
  interface StatusData {
    @Nullable
    @SerializedName("limit_remaining")
    BigDecimal limitRemaining();

    @Nullable
    BigDecimal limit();

    @Nullable
    BigDecimal usage();

    @Nullable
    @SerializedName("usage_monthly")
    BigDecimal usageMonthly();

    @Nullable
    @SerializedName("usage_daily")
    BigDecimal usageDaily();

    @Nullable
    String label();
  }
}

public class AiCommands {
  private static final @NotNull Logger LOGGER = LoggerFactory.getLogger(AiCommands.class);
  private static final @NotNull ExecutorService EXECUTOR =
      Executors.newVirtualThreadPerTaskExecutor();

  private final @NotNull LlmHandler llmHandler;

  public AiCommands(@NotNull LlmHandler llmHandler) {
    this.llmHandler = llmHandler;
  }

  private static @NotNull URI keyEndpoint(@NotNull String endpoint) {
    var base = endpoint.replaceAll("/+$", "").replaceFirst("/chat/completions$", "");
    return URI.create(base + "/key");
  }

  private static @NotNull String formatKeyStatus(@NotNull KeyStatusResponse.StatusData response) {
    return formatKeyStatus(
        response.limitRemaining(),
        response.limit(),
        response.usage(),
        response.usageMonthly(),
        response.usageDaily(),
        response.label());
  }

  private static @NotNull String formatKeyStatus(
      @Nullable BigDecimal limitRemaining,
      @Nullable BigDecimal limit,
      @Nullable BigDecimal usage,
      @Nullable BigDecimal usageMonthly,
      @Nullable BigDecimal usageDaily,
      @Nullable String label) {
    var message = new StringBuilder("OpenRouter API Key Status:\n");
    message.append(" - Credit remaining: ").append(formatMoney(limitRemaining)).append('\n');
    if (limit != null) {
      message.append(" - Limit: ").append(formatMoney(limit)).append('\n');
    }
    if (usage != null) {
      message.append(" - Usage: ").append(formatMoney(usage));
      var details = new java.util.ArrayList<String>();
      if (usageMonthly != null) {
        details.add("monthly: " + formatMoney(usageMonthly));
      }
      if (usageDaily != null) {
        details.add("daily: " + formatMoney(usageDaily));
      }
      if (!details.isEmpty()) {
        message.append(" (").append(String.join(", ", details)).append(')');
      }
      message.append('\n');
    }
    if (label != null) {
      message.append(" - Key label: ").append(label).append('\n');
    }
    return message.toString().stripTrailing();
  }

  private static @NotNull String formatMoney(@Nullable BigDecimal amount) {
    if (amount == null) {
      return "Unlimited";
    }
    var scaled = amount.stripTrailingZeros();
    if (scaled.scale() < 2) {
      scaled = scaled.setScale(2, RoundingMode.HALF_UP);
    }
    return "$" + scaled.toPlainString() + " USD";
  }

  public void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
    var root =
        Commands.literal("ai")
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(this.clear())
            .then(this.reload());
    root.then(this.models());
    root.then(this.money());
    root.then(this.bans());
    dispatcher.register(root);
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> clear() {
    return Commands.literal("clear")
        .executes(
            context -> {
              this.llmHandler.clearHistory();
              context
                  .getSource()
                  .sendSuccess(
                      () -> Component.literal("AI history cleared; pending replies discarded."),
                      true);
              return 1;
            });
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> reload() {
    return Commands.literal("reload")
        .executes(
            context ->
                this.perform(
                    context.getSource(),
                    "reload AI config",
                    () -> {
                      var config = ConfigHelper.reloadConfig();
                      var llm = config.llm();
                      if (llm == null) {
                        this.llmHandler.close();
                      } else {
                        this.llmHandler.replaceSession(llm);
                      }
                      context
                          .getSource()
                          .sendSuccess(
                              () -> Component.literal("AI config reloaded; history cleared."),
                              true);
                      return 1;
                    }));
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> models() {
    return Commands.literal("models")
        .executes(context -> this.listModels(context.getSource()))
        .then(Commands.literal("list").executes(context -> this.listModels(context.getSource())))
        .then(this.modelsAdd())
        .then(this.modelsRemove());
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> modelsAdd() {
    var triggerArg = Commands.argument("trigger", StringArgumentType.word());
    var modelArg = Commands.argument("model", StringArgumentType.greedyString());
    return Commands.literal("add")
        .then(
            triggerArg.then(
                modelArg.executes(
                    context -> {
                      var trigger =
                          StringArgumentType.getString(context, triggerArg.getName()).trim();
                      var model = StringArgumentType.getString(context, modelArg.getName()).trim();
                      if (trigger.endsWith(":") || trigger.endsWith(",")) {
                        context
                            .getSource()
                            .sendFailure(
                                Component.literal("Trigger name must not end with ':' or ','."));
                        return 0;
                      }
                      var currentLlm = this.llmConfig(context.getSource());
                      if (currentLlm == null) {
                        return 0;
                      }
                      return this.perform(
                          context.getSource(),
                          "add model trigger",
                          () -> {
                            var triggers = new HashMap<>(currentLlm.triggers());
                            triggers.put(trigger.toLowerCase(Locale.ROOT), model);
                            var updated =
                                ImmutableLlmConfig.builder()
                                    .from(currentLlm)
                                    .triggers(Map.copyOf(triggers))
                                    .build();
                            this.saveLlmConfig(updated);
                            context
                                .getSource()
                                .sendSuccess(
                                    () ->
                                        Component.literal(
                                            "Added AI trigger '"
                                                + trigger
                                                + "' -> '"
                                                + model
                                                + "'."),
                                    true);
                            return 1;
                          });
                    })));
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> modelsRemove() {
    return Commands.literal("remove")
        .then(
            Commands.argument("trigger", StringArgumentType.word())
                .suggests(
                    (context, builder) ->
                        SharedSuggestionProvider.suggest(this.modelTriggers(), builder))
                .executes(
                    context -> {
                      var trigger =
                          StringArgumentType.getString(context, "trigger")
                              .trim()
                              .toLowerCase(Locale.ROOT);
                      var currentLlm = this.llmConfig(context.getSource());
                      if (currentLlm == null) {
                        return 0;
                      }
                      return this.perform(
                          context.getSource(),
                          "remove model trigger",
                          () -> {
                            var triggers = new HashMap<>(currentLlm.triggers());
                            if (!triggers.containsKey(trigger)) {
                              context
                                  .getSource()
                                  .sendFailure(
                                      Component.literal(
                                          "Trigger '" + trigger + "' is not configured."));
                              return 0;
                            }
                            if (triggers.size() <= 1) {
                              context
                                  .getSource()
                                  .sendFailure(
                                      Component.literal("Cannot remove the last trigger."));
                              return 0;
                            }
                            triggers.remove(trigger);
                            var updated =
                                ImmutableLlmConfig.builder()
                                    .from(currentLlm)
                                    .triggers(Map.copyOf(triggers))
                                    .build();
                            this.saveLlmConfig(updated);
                            context
                                .getSource()
                                .sendSuccess(
                                    () ->
                                        Component.literal("Removed AI trigger '" + trigger + "'."),
                                    true);
                            return 1;
                          });
                    }));
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> money() {
    return Commands.literal("money")
        .executes(
            context -> {
              var source = context.getSource();
              var llm = this.llmConfig(source);
              if (llm == null) {
                return 0;
              }
              var token = llm.bearerToken();
              if (token == null || token.isBlank()) {
                source.sendFailure(Component.literal("No API token is configured in owosmp.json."));
                return 0;
              }

              var endpoint = keyEndpoint(llm.endpoint());
              source.sendSuccess(() -> Component.literal("Checking OpenRouter balance..."), false);
              EXECUTOR.execute(
                  () -> {
                    try {
                      var client =
                          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                      var request =
                          HttpRequest.newBuilder(endpoint)
                              .header("Authorization", "Bearer " + token)
                              .header("Accept", "application/json")
                              .timeout(llm.keyRequestTimeout())
                              .GET()
                              .build();
                      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                      if (response.statusCode() != 200) {
                        source
                            .getServer()
                            .execute(
                                () ->
                                    source.sendFailure(
                                        Component.literal(
                                            "OpenRouter API returned HTTP "
                                                + response.statusCode()
                                                + ": "
                                                + response.body())));
                        return;
                      }

                      var responseData =
                          GsonHolder.gson()
                              .fromJson(response.body(), new TypeToken<KeyStatusResponse>() {});
                      if (responseData == null) {
                        throw new IllegalStateException(
                            "OpenRouter returned an empty key status response.");
                      }
                      var message =
                          responseData.data() == null
                              ? formatKeyStatus(
                                  responseData.limitRemaining(),
                                  responseData.limit(),
                                  responseData.usage(),
                                  responseData.usageMonthly(),
                                  responseData.usageDaily(),
                                  responseData.label())
                              : formatKeyStatus(responseData.data());
                      source
                          .getServer()
                          .execute(
                              () -> source.sendSuccess(() -> Component.literal(message), false));
                    } catch (IOException | InterruptedException | RuntimeException ex) {
                      if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                      }
                      LOGGER.error("Failed to query OpenRouter key balance", ex);
                      source
                          .getServer()
                          .execute(
                              () ->
                                  source.sendFailure(
                                      Component.literal(
                                          "Failed to query OpenRouter balance: "
                                              + ex.getMessage())));
                    }
                  });
              return 1;
            });
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> bans() {
    return Commands.literal("bans")
        .then(this.bansList())
        .then(Commands.literal("add").then(this.bansChange(true)))
        .then(Commands.literal("remove").then(this.bansChange(false)))
        .then(Commands.literal("rm").then(this.bansChange(false)));
  }

  private @NotNull LiteralArgumentBuilder<CommandSourceStack> bansList() {
    return Commands.literal("list")
        .executes(
            context -> {
              var llm = this.llmConfig(context.getSource());
              if (llm == null) {
                return 0;
              }
              var banned = llm.bannedPlayers();
              if (banned.isEmpty()) {
                context
                    .getSource()
                    .sendSuccess(
                        () -> Component.literal("No players are banned from AI chat."), false);
                return 0;
              }
              var message = new StringBuilder("Players banned from AI chat:\n");
              banned.stream()
                  .map(java.util.UUID::toString)
                  .sorted()
                  .forEach(uuid -> message.append(" - ").append(uuid).append('\n'));
              context
                  .getSource()
                  .sendSuccess(() -> Component.literal(message.toString().stripTrailing()), false);
              return banned.size();
            });
  }

  private @NotNull RequiredArgumentBuilder<CommandSourceStack, Result> bansChange(boolean ban) {
    return Commands.argument("players", GameProfileArgument.gameProfile())
        .executes(
            context -> {
              var players = GameProfileArgument.getGameProfiles(context, "players");
              var currentLlm = this.llmConfig(context.getSource());
              if (currentLlm == null) {
                return 0;
              }
              return this.perform(
                  context.getSource(),
                  ban ? "ban players from AI chat" : "unban players from AI chat",
                  () -> {
                    var banned = new HashSet<>(currentLlm.bannedPlayers());
                    int changed = 0;
                    for (var player : players) {
                      if (ban ? banned.add(player.id()) : banned.remove(player.id())) {
                        changed++;
                      }
                    }
                    var updated =
                        ImmutableLlmConfig.builder()
                            .from(currentLlm)
                            .bannedPlayers(Set.copyOf(banned))
                            .build();
                    this.saveLlmConfig(updated);
                    var changedPlayers = changed;
                    context
                        .getSource()
                        .sendSuccess(
                            () ->
                                Component.literal(
                                    (ban ? "Banned " : "Unbanned ")
                                        + changedPlayers
                                        + " player(s) from AI chat."),
                            true);
                    return changed;
                  });
            });
  }

  private int listModels(@NotNull CommandSourceStack source) {
    var config = this.llmConfig(source);
    if (config == null) {
      return 0;
    }
    var triggers = config.triggers();
    if (triggers.isEmpty()) {
      source.sendSuccess(() -> Component.literal("No AI models/triggers configured."), false);
      return 0;
    }

    var message = new StringBuilder("AI models (").append(triggers.size()).append("):\n");
    triggers.forEach(
        (trigger, model) ->
            message.append(" - ").append(trigger).append(": ").append(model).append('\n'));
    source.sendSuccess(() -> Component.literal(message.toString().stripTrailing()), false);
    return triggers.size();
  }

  private @Nullable LlmConfig llmConfig(@NotNull CommandSourceStack source) {
    var llm = ConfigHelper.getConfig().llm();
    if (llm == null) {
      source.sendFailure(Component.literal("No LLM configuration found."));
    }
    return llm;
  }

  private Set<String> modelTriggers() {
    var llm = ConfigHelper.getConfig().llm();
    return llm == null ? Set.of() : llm.triggers().keySet();
  }

  private void saveLlmConfig(@NotNull LlmConfig llm) throws IOException {
    var config = ImmutableOwoSmpConfig.builder().from(ConfigHelper.getConfig()).llm(llm).build();
    ConfigHelper.writeConfig(config);
    this.llmHandler.updateConfig(llm);
  }

  private int perform(
      @NotNull CommandSourceStack source, @NotNull String action, @NotNull IntOperation operation) {
    try {
      return operation.run();
    } catch (IOException
        | com.google.gson.JsonParseException
        | IllegalArgumentException
        | IllegalStateException ex) {
      return this.failure(source, action, ex);
    } catch (RuntimeException ex) {
      if (ex.getCause() instanceof IOException) {
        return this.failure(source, action, ex);
      }
      throw ex;
    }
  }

  private int failure(
      @NotNull CommandSourceStack source, @NotNull String action, @NotNull Exception error) {
    LOGGER.error("Failed to {}", action, error);
    source.sendFailure(
        Component.literal("Failed to " + action + "; see the server log for details."));
    return 0;
  }

  @FunctionalInterface
  private interface IntOperation {
    int run() throws IOException;
  }
}
