package dev.mardroemmar.owosmp.mixin.minecarts;

import dev.mardroemmar.owosmp.GameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.phys.shapes.MinecartCollisionContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author aurieh
 */
@Mixin(MinecartCollisionContext.class)
abstract class MinecartCollisionContextMixin {
  @SuppressWarnings("SpellCheckingInspection") // This is Mojang's fault.
  @Shadow
  private @Nullable BlockPos ingoreBelow;

  @Shadow private @Nullable BlockPos slopeIgnore;

  /** Permits old behaviour Minecarts to collide. */
  @Inject(method = "<init>", at = @At("TAIL"))
  private void oldCartsCollideNormally(
      final @NotNull AbstractMinecart entity,
      final boolean alwaysStandOnFluid,
      final @NotNull CallbackInfo ci) {
    if (entity.getBehavior() instanceof OldMinecartBehavior
        && entity.level() instanceof ServerLevel sl
        && sl.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS)) {
      this.ingoreBelow = null;
      this.slopeIgnore = null;
    }
  }
}
