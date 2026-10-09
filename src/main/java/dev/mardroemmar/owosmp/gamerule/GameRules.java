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
  public static final GameRule<Boolean> ENDERMAN_GRIEFING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("enderman_griefing"));
  public static final GameRule<Boolean> ENDER_DRAGON_GRIEFING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("ender_dragon_griefing"));
  public static final GameRule<Boolean> WITHER_GRIEFING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("wither_griefing"));
  public static final GameRule<Boolean> WITHER_PLACE_WITHER_ROSE =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("wither_place_wither_rose"));
  public static final GameRule<Boolean> CREEPER_GRIEFING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("creeper_griefing"));
  public static final GameRule<Boolean> MOBS_EAT_GRASS_INTO_DIRT =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mobs_eat_grass_into_dirt"));
  public static final GameRule<Boolean> RAVAGER_GRIEFING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("ravager_griefing"));
  public static final GameRule<Boolean> MOBS_PICK_UP_DROPPED_ITEMS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mobs_pick_up_dropped_items"));
  public static final GameRule<Boolean> EVOKER_WOLOLO_SHEEP =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("evoker_wololo_sheep"));
  public static final GameRule<Boolean> MOBS_BREAK_DOORS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mobs_break_doors"));
  public static final GameRule<Boolean> RABBIT_RAID_GARDEN =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("rabbit_raid_garden"));
  public static final GameRule<Boolean> POWDER_SNOW_MELTS_ON_EXTINGUISH_MOB =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("powder_snow_melts_on_extinguish_mob"));
  public static final GameRule<Boolean> TNT_SULFUR_CUBE_EXPLODES =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("tnt_sulfur_cube_explodes"));
  public static final GameRule<Boolean> FOX_EATS_BERRIES =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("fox_eats_berries"));
  public static final GameRule<Boolean> SILVERFISH_GRIEFING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("silverfish_griefing"));
  public static final GameRule<Boolean> ALLAY_PICK_UP_DROPPED_ITEMS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("allay_pick_up_dropped_items"));
  public static final GameRule<Boolean> SNOW_GOLEM_SPREADS_SNOW =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("snow_golem_spreads_snow"));
  public static final GameRule<Boolean> NAUTILUS_ATTACK_ARMOR_STANDS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("nautilus_attack_armor_stands"));
  public static final GameRule<Boolean> GOAT_ATTACK_ARMOR_STANDS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("goat_attack_armor_stands"));
  public static final GameRule<Boolean> VILLAGERS_FARM_CROPS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("villagers_farm_crops"));
  public static final GameRule<Boolean> MOB_EXPLOSIONS_DESTROY_VEHICLES =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mob_explosions_destroy_vehicles"));
  public static final GameRule<Boolean> ARMOR_STAND_DIE_TO_MOBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("armor_stand_die_to_mobs"));
  public static final GameRule<Boolean> WEAVING_MOBS_PLACE_COBWEBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("weaving_mobs_place_cobwebs"));
  public static final GameRule<Boolean> CUSHION_DIE_TO_MOBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("cushion_die_to_mobs"));
  public static final GameRule<Boolean> ITEM_FRAME_DIE_TO_MOBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("item_frame_die_to_mobs"));
  public static final GameRule<Boolean> ITEM_FRAME_GLOW_DIE_TO_MOBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("item_frame_glow_die_to_mobs"));
  public static final GameRule<Boolean> PAINTING_DIE_TO_MOBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("painting_die_to_mobs"));
  public static final GameRule<Boolean> LEASH_FENCE_KNOT_DIE_TO_MOBS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("leash_fence_knot_die_to_mobs"));
  public static final GameRule<Boolean> PIGLIN_PICK_UP_DROPPED_ITEMS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("piglin_pick_up_dropped_items"));
  public static final GameRule<Boolean> MOBS_DESTROY_TURTLE_EGGS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mobs_destroy_turtle_eggs"));
  public static final GameRule<Boolean> FIREBALLS_DESTROY_BLOCKS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("fireballs_destroy_blocks"));
  public static final GameRule<Boolean> MOB_PROJECTILE_INTERACT_WITH_BLOCKS =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mob_projectile_interact_with_blocks"));
  public static final GameRule<Boolean> MOBS_TRAMPLE_FARMLAND =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("mobs_trample_farmland"));
  public static final GameRule<Boolean> PLAYERS_TRAMPLE_FARMLAND =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("players_trample_farmland"));
  public static final GameRule<Boolean> PLAYERS_TRAMPLE_FARMLAND_WITH_FEATHER_FALLING =
      GameRuleBuilder.forBoolean(true)
          .category(GameRuleCategory.MOBS)
          .buildAndRegister(id("players_trample_farmland_with_feather_falling"));
  public static final GameRule<Boolean> GLOBAL_VILLAGER_CURE_REPUTATION =
      GameRuleBuilder.forBoolean(false)
          .category(GameRuleCategory.MISC)
          .buildAndRegister(id("global_villager_cure_reputation"));

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

    var onlyIfMobGriefing =
        (UpdateCallback<Boolean>)
            (source, _, _) -> {
              var mobGriefingRule = net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING;
              var mobGriefing = source.getLevel().getGameRules().get(mobGriefingRule);
              if (!mobGriefing) {
                source.sendSystemMessage(
                    Component.empty()
                        .append(
                            Component.literal("WARN:")
                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                        .append(
                            " This game rule only applies if the '"
                                + mobGriefingRule.getIdentifier()
                                + "' gamerule is enabled."));
              }
            };
    registerUpdateCallback(ENDERMAN_GRIEFING, onlyIfMobGriefing);
    registerUpdateCallback(ENDER_DRAGON_GRIEFING, onlyIfMobGriefing);
    registerUpdateCallback(WITHER_GRIEFING, onlyIfMobGriefing);
    registerUpdateCallback(WITHER_PLACE_WITHER_ROSE, onlyIfMobGriefing);
    registerUpdateCallback(CREEPER_GRIEFING, onlyIfMobGriefing);
    registerUpdateCallback(MOBS_EAT_GRASS_INTO_DIRT, onlyIfMobGriefing);
    registerUpdateCallback(RAVAGER_GRIEFING, onlyIfMobGriefing);
    registerUpdateCallback(MOBS_PICK_UP_DROPPED_ITEMS, onlyIfMobGriefing);
    registerUpdateCallback(MOBS_BREAK_DOORS, onlyIfMobGriefing);
    registerUpdateCallback(RABBIT_RAID_GARDEN, onlyIfMobGriefing);
    registerUpdateCallback(EVOKER_WOLOLO_SHEEP, onlyIfMobGriefing);
    registerUpdateCallback(POWDER_SNOW_MELTS_ON_EXTINGUISH_MOB, onlyIfMobGriefing);
    registerUpdateCallback(TNT_SULFUR_CUBE_EXPLODES, onlyIfMobGriefing);
    registerUpdateCallback(FOX_EATS_BERRIES, onlyIfMobGriefing);
    registerUpdateCallback(SILVERFISH_GRIEFING, onlyIfMobGriefing);
    registerUpdateCallback(ALLAY_PICK_UP_DROPPED_ITEMS, onlyIfMobGriefing);
    registerUpdateCallback(SNOW_GOLEM_SPREADS_SNOW, onlyIfMobGriefing);
    registerUpdateCallback(NAUTILUS_ATTACK_ARMOR_STANDS, onlyIfMobGriefing);
    registerUpdateCallback(GOAT_ATTACK_ARMOR_STANDS, onlyIfMobGriefing);
    registerUpdateCallback(MOB_EXPLOSIONS_DESTROY_VEHICLES, onlyIfMobGriefing);
    registerUpdateCallback(ARMOR_STAND_DIE_TO_MOBS, onlyIfMobGriefing);
    registerUpdateCallback(WEAVING_MOBS_PLACE_COBWEBS, onlyIfMobGriefing);
    registerUpdateCallback(CUSHION_DIE_TO_MOBS, onlyIfMobGriefing);
    registerUpdateCallback(ITEM_FRAME_DIE_TO_MOBS, onlyIfMobGriefing);
    registerUpdateCallback(ITEM_FRAME_GLOW_DIE_TO_MOBS, onlyIfMobGriefing);
    registerUpdateCallback(PAINTING_DIE_TO_MOBS, onlyIfMobGriefing);
    registerUpdateCallback(LEASH_FENCE_KNOT_DIE_TO_MOBS, onlyIfMobGriefing);
    registerUpdateCallback(PIGLIN_PICK_UP_DROPPED_ITEMS, onlyIfMobGriefing);
    registerUpdateCallback(MOBS_DESTROY_TURTLE_EGGS, onlyIfMobGriefing);
    registerUpdateCallback(FIREBALLS_DESTROY_BLOCKS, onlyIfMobGriefing);
    registerUpdateCallback(MOB_PROJECTILE_INTERACT_WITH_BLOCKS, onlyIfMobGriefing);
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
