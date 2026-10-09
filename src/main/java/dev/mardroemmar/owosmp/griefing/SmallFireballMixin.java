package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SmallFireball.class)
abstract class SmallFireballMixin {
  @WrapOperation(
      method = "onHitBlock",
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
          && instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.ALLAY_PICK_UP_DROPPED_ITEMS);
    }
    return original.call(instance, gameRule);
  }
}
