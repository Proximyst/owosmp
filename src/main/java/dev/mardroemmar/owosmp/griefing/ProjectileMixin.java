package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Projectile.class)
abstract class ProjectileMixin {
  @WrapOperation(
      method = "mayInteract",
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
          && instance.get(
              dev.mardroemmar.owosmp.gamerule.GameRules.MOB_PROJECTILE_INTERACT_WITH_BLOCKS);
    }
    return original.call(instance, gameRule);
  }
}
