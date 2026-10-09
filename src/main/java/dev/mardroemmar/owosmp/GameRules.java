package dev.mardroemmar.owosmp;

import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletionCreateParams.Verbosity;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.jetbrains.annotations.NotNull;

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

  private GameRules() {}

  public static void register() {
    // We don't actually do anything here.
    // This just forces the class to load, which in turn forces the game rules to register.
  }

  private static @NotNull Identifier id(@NotNull String name) {
    return Identifier.fromNamespaceAndPath(MOD_ID, name);
  }
}
