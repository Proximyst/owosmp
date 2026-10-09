package dev.mardroemmar.owosmp.mixin.minecarts;

import dev.mardroemmar.owosmp.GameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies the new minecart behavior:
 *
 * <p>Reduces the speed for hopper minecarts when {@link GameRules#REVERT_HOPPER_MINECARTS} is set
 * to true.
 */
@Mixin(NewMinecartBehavior.class)
abstract class NewMinecartBehaviorMixin extends MinecartBehavior {
  private NewMinecartBehaviorMixin(AbstractMinecart minecart) {
    super(minecart);
  }

  /** Revert the hopper minecart speed when enabled. Ignores other minecarts. */
  @Inject(
      method = {"getMaxSpeed"},
      cancellable = true,
      at = {@At("HEAD")})
  private void getMaxSpeed(
      @NotNull ServerLevel level, @NotNull CallbackInfoReturnable<Double> cir) {
    if (this.minecart instanceof MinecartHopper) {
      // This is compatible with the REVERT_EMPTY_MINECARTS rule: we'd replace the entire Minecart
      // behaviour to old for them. Therefore, it'd simply never reach this far.
      boolean shouldRevert = level.getGameRules().get(GameRules.REVERT_HOPPER_MINECARTS);
      if (shouldRevert) {
        double speed = this.minecart.isInWater() ? 0.2 : 0.4;
        cir.setReturnValue(speed);
        cir.cancel();
      }
    }
  }
}
