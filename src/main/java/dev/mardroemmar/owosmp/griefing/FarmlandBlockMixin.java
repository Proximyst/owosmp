package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FarmlandBlock.class)
abstract class FarmlandBlockMixin {
  @WrapOperation(
      method = "fallOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/MinecraftServer;isUnderSpawnProtection(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
  private boolean onPlayerTrample(
      @NotNull MinecraftServer instance,
      @NotNull ServerLevel level,
      @NotNull BlockPos pos,
      @NotNull Player player,
      @NotNull Operation<Boolean> original) {
    if (original.call(instance, level, pos, player)) {
      return true;
    }

    var fallAttribute = player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
    var hasFeatherFalling =
        player.getAbilities().flying
            || player.getAbilities().mayfly
            || (fallAttribute != null && fallAttribute.getValue() < fallAttribute.getBaseValue());
    var gameRules = level.getGameRules();
    if (hasFeatherFalling
        && !gameRules.get(
            dev.mardroemmar.owosmp.gamerule.GameRules
                .PLAYERS_TRAMPLE_FARMLAND_WITH_FEATHER_FALLING)) {
      return true;
    }
    return !gameRules.get(dev.mardroemmar.owosmp.gamerule.GameRules.PLAYERS_TRAMPLE_FARMLAND);
  }

  @WrapOperation(
      method = "fallOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
  private @NotNull Object onGameRuleGet(
      @NotNull GameRules instance,
      @NotNull GameRule<?> gameRule,
      Operation<@NotNull Object> original,
      @Local(name = "entity", argsOnly = true) @NotNull Entity entity) {
    if (gameRule.getIdentifier().equals(GameRules.MOB_GRIEFING.getIdentifier())) {
      return (Boolean) original.call(instance, gameRule)
          && instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.MOBS_TRAMPLE_FARMLAND);
    }
    return original.call(instance, gameRule);
  }
}
