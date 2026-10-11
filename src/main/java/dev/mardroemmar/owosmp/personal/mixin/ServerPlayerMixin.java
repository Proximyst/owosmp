package dev.mardroemmar.owosmp.personal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mardroemmar.owosmp.personal.PersonalBehaviorProxy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin implements PersonalBehaviorProxy {
  @Unique
  private static final @NotNull String PERSONAL_PVP_ENABLED_KEY = "owosmp_personal_pvp_enabled";

  @Unique
  private static final @NotNull String PERSONAL_KEEP_INVENTORY_ENABLED_KEY =
      "owosmp_personal_keep_inventory_enabled";

  @Unique private boolean personalPvpEnabled = false;
  @Unique private boolean personalKeepInventoryEnabled = false;

  @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
  private void readPersonalData(@NotNull ValueInput input, @NotNull CallbackInfo ci) {
    this.personalPvpEnabled = input.getBooleanOr(PERSONAL_PVP_ENABLED_KEY, false);
    this.personalKeepInventoryEnabled =
        input.getBooleanOr(PERSONAL_KEEP_INVENTORY_ENABLED_KEY, false);
  }

  @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
  private void writePersonalData(@NotNull ValueOutput output, @NotNull CallbackInfo ci) {
    output.putBoolean(PERSONAL_PVP_ENABLED_KEY, this.personalPvpEnabled);
    output.putBoolean(PERSONAL_KEEP_INVENTORY_ENABLED_KEY, this.personalKeepInventoryEnabled);
  }

  @Override
  public boolean owosmp$personalKeepInventoryEnabled() {
    return this.personalKeepInventoryEnabled;
  }

  @Override
  public boolean owosmp$personalPvpEnabled() {
    return this.personalPvpEnabled;
  }

  @Override
  public void owosmp$setPersonalKeepInventoryEnabled(boolean enabled) {
    this.personalKeepInventoryEnabled = enabled;
  }

  @Override
  public void owosmp$setPersonalPvpEnabled(boolean enabled) {
    this.personalPvpEnabled = enabled;
  }

  @Inject(method = "restoreFrom", at = @At("HEAD"))
  private void copyPersonalData(
      @NotNull ServerPlayer oldPlayer, boolean restoreAll, @NotNull CallbackInfo ci) {
    if (oldPlayer instanceof PersonalBehaviorProxy old) {
      this.personalPvpEnabled = old.owosmp$personalPvpEnabled();
      this.personalKeepInventoryEnabled = old.owosmp$personalKeepInventoryEnabled();
    }
  }

  @WrapOperation(
      method = "restoreFrom",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
  private @NotNull Object onGameRuleGet(
      @NotNull GameRules instance,
      @NotNull GameRule<?> gameRule,
      @NotNull Operation<@NotNull Object> original) {
    if (gameRule.getIdentifier().equals(GameRules.KEEP_INVENTORY.getIdentifier())) {
      if ((Boolean) original.call(instance, gameRule)) {
        return true;
      }
      return instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.PERSONAL_KEEP_INVENTORY)
          && this.owosmp$personalKeepInventoryEnabled();
    }
    return original.call(instance, gameRule);
  }

  @WrapOperation(
      method = "canHarmPlayer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;canHarmPlayer(Lnet/minecraft/world/entity/player/Player;)Z"))
  private boolean onCanHarmPlayer(
      @NotNull ServerPlayer instance,
      @NotNull Player target,
      @NotNull Operation<@NotNull Boolean> original) {
    if (!(Boolean) original.call(instance, target)) {
      return false;
    }
    if (target instanceof PersonalBehaviorProxy proxy) {
      return !instance
              .level()
              .getGameRules()
              .get(dev.mardroemmar.owosmp.gamerule.GameRules.PERSONAL_PVP)
          || (proxy.owosmp$personalPvpEnabled() && this.owosmp$personalPvpEnabled());
    }
    return true;
  }
}
