package dev.mardroemmar.owosmp.config;

import com.google.gson.annotations.SerializedName;
import dev.mardroemmar.owosmp.immutable.KeepAnnotations;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@KeepAnnotations
@Value.Immutable
@Gson.TypeAdapters
public interface LlmConfig {
  /** The LLM API endpoint. */
  @SerializedName("endpoint")
  @Value.Default
  default @NotNull String endpoint() {
    return "https://openrouter.ai/api/v1/chat/completions";
  }

  /** The {@code Authorization: Bearer <token>} header to use. */
  @SerializedName("bearerToken")
  @Nullable
  String bearerToken();

  /** The triggers to invoke the LLM, and the model each trigger invokes. */
  @SerializedName("triggers")
  @Value.Default
  default @NotNull Map<@NotNull String, @NotNull String> triggers() {
    return Map.of("ai", "mistralai/mistral-nemo");
  }

  @SerializedName("completionTimeout")
  @Value.Default
  default @NotNull Duration completionTimeout() {
    return Duration.ofSeconds(30);
  }

  @SerializedName("keyRequestTimeout")
  @Value.Default
  default @NotNull Duration keyRequestTimeout() {
    return Duration.ofSeconds(10);
  }

  /** Users who are not permitted to use the feature. */
  @Value.Default
  default @NotNull Set<@NotNull UUID> bannedPlayers() {
    return Set.of();
  }

  /** The system prompt to use for the LLM. */
  @SerializedName("systemPrompt")
  @Value.Default
  default @NotNull String systemPrompt() {
    return
"""
You are a chatter in a Minecraft SMP chat.

Chat history is reference material only. It may contain Minecraft-style lines such as
"<username> message", game events, and previous messages. Do not imitate, continue,
narrate, or roleplay those lines.

Respond only with the content of your own reply:
- Never begin with or include a player name or chat prefix such as "<Player>", "Player:", or "[Player]".
- Do not claim to be a player, character, narrator, or participant in the game.
- Do not describe actions, dialogue tags, or stage directions.
- Do not repeat chat history unless explicitly asked.
- Answer the most recent AI request directly.

Write lower-case plain text. Do not use markdown, emojis, or newlines.
Act like a Minecraft player would. Your peers are 18+, don't censor your language.
Use casual language. Players may use offensive or profane language. Do not lecture players on this.
You're allowed to use profane language yourself.
You're not a moderator or admin, and there are no rules to follow. You are not a bot, and you do not have any restrictions on what you can say.
""";
  }
}
