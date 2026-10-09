package dev.mardroemmar.owosmp.minecarts.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import dev.mardroemmar.owosmp.minecarts.MinecartBehaviorProxy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author aurieh
 */
@Mixin(AbstractMinecart.class)
abstract class AbstractMinecartMixin implements MinecartBehaviorProxy {
  @Shadow @Final @Mutable private @NotNull MinecartBehavior behavior;

  @Unique private @Nullable NewMinecartBehavior syncProxy;

  /** Switch the Minecart's behaviour depending on what's riding in it. */
  @Inject(method = "tick", at = @At("HEAD"))
  private void selectBehaviorInTick(final @NotNull CallbackInfo ci) {
    var self = (AbstractMinecart) (Object) this;
    if (!(self.level() instanceof ServerLevel sl)
        || !AbstractMinecart.useExperimentalMovement(self.level())) {
      return;
    }
    if (!sl.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS)) {
      return;
    }

    var wantsNew = self.getFirstPassenger() instanceof Player;
    if (wantsNew == this.behavior instanceof NewMinecartBehavior) {
      return;
    }

    // The new and old behaviours have different coordinate system interpretations,
    // so we need to flip the minecart when switching between them.
    self.setYRot(180.0F - self.getYRot());
    self.setFlipped(!self.isFlipped());
    self.setXRot(0.0F);
    self.yRotO = self.getYRot();
    self.xRotO = self.getXRot();

    // And once we've done so, we need to adjust the cart again.
    if (wantsNew) {
      var newBehavior = new NewMinecartBehavior(self);
      this.behavior = newBehavior;
      var pos = self.getCurrentBlockPosOrRailBelow();
      newBehavior.adjustToRails(pos, sl.getBlockState(pos), true);
    } else {
      this.behavior = new OldMinecartBehavior(self);
    }
  }

  /**
   * Decide whether we're using the new or old Minecart behaviour based on game rule and passenger
   * type.
   */
  @WrapOperation(
      method = {
        "getCurrentBlockPosOrRailBelow",
        "move",
        "applyEffectsFromBlocks()V",
        "pushOtherMinecart"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;useExperimentalMovement(Lnet/minecraft/world/level/Level;)Z"))
  private boolean usesNewBehavior(@NotNull Level level, @NotNull Operation<Boolean> original) {
    return (level instanceof ServerLevel sl
            && sl.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS))
        ? original.call(level) && this.behavior instanceof NewMinecartBehavior
        : original.call(level);
  }

  /** Ensure we save the rotation in the new format, even when using old behaviour. */
  @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
  private void saveRotationAsNew(
      final @NotNull ValueOutput output, final @NotNull CallbackInfo ci) {
    var self = (AbstractMinecart) (Object) this;
    if (this.behavior instanceof OldMinecartBehavior
        && AbstractMinecart.useExperimentalMovement(self.level())
        && self.level() instanceof ServerLevel sl
        && sl.getGameRules().get(GameRules.REVERT_EMPTY_MINECARTS)) {
      output.store("Rotation", Vec2.CODEC, new Vec2(180.0F - self.getYRot(), 0.0F));
      output.putBoolean("FlippedRotation", !self.isFlipped());
    }
  }

  @Override
  public @NotNull NewMinecartBehavior owosmp$syncProxy() {
    if (this.syncProxy == null) {
      this.syncProxy = new NewMinecartBehavior((AbstractMinecart) (Object) this);
    }

    return this.syncProxy;
  }
}
