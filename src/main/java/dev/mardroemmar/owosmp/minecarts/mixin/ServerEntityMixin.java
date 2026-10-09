package dev.mardroemmar.owosmp.minecarts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import dev.mardroemmar.owosmp.minecarts.MinecartBehaviorProxy;
import java.util.Objects;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * @author aurieh
 */
@Mixin(ServerEntity.class)
class ServerEntityMixin {
  @Shadow @Final private @NotNull ServerLevel level;

  /** Synchronise old-behaving Minecarts using the old behaviour to the client. */
  @WrapOperation(
      method = "sendChanges",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;getBehavior()Lnet/minecraft/world/entity/vehicle/minecart/MinecartBehavior;"))
  private @NotNull MinecartBehavior syncOldCartsAsNew(
      @NotNull AbstractMinecart cart, @NotNull Operation<MinecartBehavior> original) {
    MinecartBehavior behavior = original.call(cart);
    return behavior instanceof OldMinecartBehavior
            && AbstractMinecart.useExperimentalMovement(cart.level())
            && level.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS)
        ? ((MinecartBehaviorProxy) cart).owosmp$syncProxy()
        : behavior;
  }

  /**
   * Synchronise the correct Y-rotation; the coordinate systems aren't equal in the old and new
   * behaviours.
   */
  @WrapOperation(
      method = {"<init>", "sendChanges", "handleMinecartPosRot"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
  private float syncYRot(final @NotNull Entity entity, final @NotNull Operation<Float> original) {
    var yRot = original.call(entity);
    return entity instanceof AbstractMinecart cart
            && cart.getBehavior() instanceof OldMinecartBehavior
            && AbstractMinecart.useExperimentalMovement(cart.level())
            && level.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS)
        ? 180.0F - yRot
        : yRot;
  }

  /**
   * Synchronise the correct X-rotation; the coordinate systems aren't equal in the old and new
   * behaviours.
   */
  @WrapOperation(
      method = {"<init>", "sendChanges", "handleMinecartPosRot"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getXRot()F"))
  private float syncXRot(final @NotNull Entity entity, final @NotNull Operation<Float> original) {
    if (entity instanceof AbstractMinecart cart
        && cart.getBehavior() instanceof OldMinecartBehavior oldBehavior
        && AbstractMinecart.useExperimentalMovement(cart.level())
        && level.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS)) {
      var x = cart.getX();
      var y = cart.getY();
      var z = cart.getZ();
      var pos = oldBehavior.getPos(x, y, z);
      if (pos == null) {
        return 0.0F;
      }

      var front = Objects.requireNonNullElse(oldBehavior.getPosOffs(x, y, z, 0.3F), pos);
      var back = Objects.requireNonNullElse(oldBehavior.getPosOffs(x, y, z, -0.3F), pos);
      var direction = back.subtract(front);
      if (direction.length() == 0.0) {
        return 0.0F;
      }

      direction = direction.normalize();
      var railYRot = (float) (Math.atan2(direction.z, direction.x) * 180.0 / Math.PI);
      var xRot = (float) (Math.atan(direction.y) * 73.0);
      return Math.abs(Mth.wrapDegrees(cart.getYRot() - railYRot)) > 90.0F ? -xRot : xRot;
    } else {
      return original.call(entity);
    }
  }
}
