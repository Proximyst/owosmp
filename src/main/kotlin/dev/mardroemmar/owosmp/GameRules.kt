package dev.mardroemmar.owosmp

import dev.mardroemmar.owosmp.OwoSmp.Companion.MOD_ID
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder
import net.minecraft.resources.Identifier
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleCategory

object GameRules {
  /**
   * Load the game rules into the game.
   *
   * This does nothing but ensure the class is loaded.
   */
  fun load() = Unit

  val revertHopperMinecartsIdentifier: Identifier =
    Identifier.fromNamespaceAndPath(MOD_ID, "revert_hopper_minecart_speed")

  val revertHopperMinecarts: GameRule<Boolean> =
    GameRuleBuilder.forBoolean(false)
      .category(GameRuleCategory.MISC)
      .buildAndRegister(revertHopperMinecartsIdentifier)

  val disableLlmIdentifier: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "disable_llm")

  val disableLlm: GameRule<Boolean> =
    GameRuleBuilder.forBoolean(false)
      .category(GameRuleCategory.CHAT)
      .buildAndRegister(disableLlmIdentifier)

  val respawnDragonEggIdentifier: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "respawn_dragon_egg")

  val respawnDragonEgg: GameRule<Boolean> =
    GameRuleBuilder.forBoolean(false)
      .category(GameRuleCategory.MOBS)
      .buildAndRegister(respawnDragonEggIdentifier)
}
