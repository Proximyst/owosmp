package dev.mardroemmar.owosmp.mixin.minecarts;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mardroemmar.owosmp.GameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * @author aurieh
 */
@Mixin(MinecartItem.class)
class MinecartItemMixin {
  /** Permits overlapping Minecarts if they use the old behaviour. */
  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;useExperimentalMovement(Lnet/minecraft/world/level/Level;)Z"))
  private boolean skipOverlapCheck(
      final @NotNull Level level, final @NotNull Operation<Boolean> original) {
    return original.call(level)
        && (!(level instanceof ServerLevel sl)
            || !sl.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS));
  }
}
