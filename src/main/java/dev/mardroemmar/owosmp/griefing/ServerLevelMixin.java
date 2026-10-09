package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Disable creeper/wither griefing if the creeper/wither griefing game rules are disabled. */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
  @WrapOperation(
      method = "explode",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
  private @NotNull Object onGameRuleGet(
      @NotNull GameRules instance,
      @NotNull GameRule<?> gameRule,
      Operation<@NotNull Object> original,
      @Local(name = "source", argsOnly = true) @Nullable Entity source) {
    if (gameRule.getIdentifier().equals(GameRules.MOB_GRIEFING.getIdentifier())) {
      if (!(Boolean) original.call(instance, gameRule)) {
        return false;
      }
      return switch (source) {
        case Creeper _ -> instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.CREEPER_GRIEFING);
        case WitherSkull _, WitherBoss _ ->
            instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.WITHER_GRIEFING);
        default -> true;
      };
    }
    return original.call(instance, gameRule);
  }
}
