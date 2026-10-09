package dev.mardroemmar.owosmp.gamerule;

import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionCreateParams.Verbosity;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers and stores game rules for the mod.
 *
 * <p>These are loaded with {@link ServerLevel#getGameRules()} or {@link
 * MinecraftServer#getGameRules()}, through {@link
 * net.minecraft.world.level.gamerules.GameRules#get(GameRule)}.
 */
public class GameRules {
  private static final String MOD_ID = "owosmp";

  public static final GameRule<Boolean> REVERT_HOPPER_MINECARTS =
      GameRuleBuilder.forBoolean(false)
          .category(GameRuleCategory.MISC)
          .buildAndRegister(id("revert_hopper_minecarts"));
  public static final GameRule<Boolean> REVERT_EMPTY_MINECARTS =
      GameRuleBuilder.forBoolean(false)
          .category(GameRuleCategory.MISC)
          .buildAndRegister(id("revert_empty_minecarts"));

  public static final GameRule<Boolean> DISABLE_LLM =
      GameRuleBuilder.forBoolean(false)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("disable_llm"));
  public static final GameRule<Integer> LLM_HISTORY =
      GameRuleBuilder.forInteger(40)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_history"));
  public static final GameRule<Boolean> LLM_INCLUDE_SYSTEM_PROMPT =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_include_system_prompt"));
  public static final GameRule<Boolean> LLM_INCLUDE_PLAYER_CONNECTIONS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_include_player_connections"));
  public static final GameRule<Boolean> LLM_INCLUDE_PLAYER_DEATHS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_include_player_deaths"));
  public static final GameRule<Boolean> LLM_INCLUDE_PLAYER_CHAT =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_include_player_chat"));
  public static final GameRule<ReasoningEffort.Known> LLM_REASONING_EFFORT =
      GameRuleBuilder.forEnum(ReasoningEffort.Known.LOW)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_reasoning_effort"));
  public static final GameRule<Verbosity.Known> LLM_VERBOSITY =
      GameRuleBuilder.forEnum(Verbosity.Known.LOW)
          .category(GameRuleCategory.CHAT)
          .buildAndRegister(id("llm_verbosity"));

  public static final GameRule<Boolean> RESPAWN_DRAGON_EGG =
      GameRuleBuilder.forBoolean(false)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("respawn_dragon_egg"));

  private static final @NotNull Map<Identifier, UpdateCallback<? super Object>> updateCallbacks =
      new HashMap<>();

  private GameRules() {}

  public static void register() {
    registerUpdateCallback(
        REVERT_HOPPER_MINECARTS,
        (source, _, _) -> {
          if (!AbstractMinecart.useExperimentalMovement(source.getLevel())) {
            source.sendSystemMessage(
                Component.empty()
                    .append(
                        Component.literal("WARN:")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(
                        " This game rule only applies to new minecart behavior, which is disabled in this world."));
          }
        });
    registerUpdateCallback(
        REVERT_EMPTY_MINECARTS,
        (source, _, _) -> {
          if (!AbstractMinecart.useExperimentalMovement(source.getLevel())) {
            source.sendSystemMessage(
                Component.empty()
                    .append(
                        Component.literal("WARN:")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(
                        " This game rule only applies to new minecart behavior, which is disabled in this world."));
          }
        });
    registerUpdateCallback(
        LLM_REASONING_EFFORT,
        (source, _, newValue) -> {
          if (newValue.ordinal() >= ReasoningEffort.Known.HIGH.ordinal()) {
            source.sendSystemMessage(
                Component.empty()
                    .append(
                        Component.literal("WARN:")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(
                        " High reasoning effort may result in increased latency and costs for LLM responses. The quality does not tend to be better than LOW/MEDIUM for our use-case."));
          }
        });
  }

  // The generics are elided and quite weird with the mixin.
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T> void registerUpdateCallback(
      @NotNull GameRule<T> gameRule, @NotNull UpdateCallback<T> callback) {
    updateCallbacks.put(gameRule.getIdentifier(), (UpdateCallback) callback);
  }

  public static @Nullable UpdateCallback<? super Object> getUpdateCallback(@NotNull Identifier id) {
    return updateCallbacks.get(id);
  }

  /** Create mod-owned identifiers for game rules. */
  private static @NotNull Identifier id(@NotNull String name) {
    return Identifier.fromNamespaceAndPath(MOD_ID, name);
  }

  @FunctionalInterface
  public interface UpdateCallback<T> {
    void onUpdate(
        @NotNull CommandSourceStack source,
        @NotNull GameRule<? extends @NotNull T> gameRule,
        @NotNull T newValue);
  }
}
