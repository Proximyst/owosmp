package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Evoker.EvokerWololoSpellGoal.class)
abstract class EvokerWololoSpellGoalMixin {
  @WrapOperation(
      method = "canUse",
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
          && instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.EVOKER_WOLOLO_SHEEP);
    }
    return original.call(instance, gameRule);
  }
}
