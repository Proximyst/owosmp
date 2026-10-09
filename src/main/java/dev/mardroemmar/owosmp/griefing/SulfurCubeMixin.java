package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SulfurCube.class)
abstract class SulfurCubeMixin {
  @WrapOperation(
      method = "tickFuse",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
  private @NotNull Object onGameRuleGet(
      @NotNull GameRules instance,
      @NotNull GameRule<?> gameRule,
      Operation<@NotNull Object> original) {
    if (gameRule.getIdentifier().equals(GameRules.MOB_GRIEFING.getIdentifier())) {
      return (Boolean) original.call(instance, gameRule)
          && instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.TNT_SULFUR_CUBE_EXPLODES);
    }
    return original.call(instance, gameRule);
  }
}
