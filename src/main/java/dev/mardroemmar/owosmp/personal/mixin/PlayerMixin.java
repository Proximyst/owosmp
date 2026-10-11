package dev.mardroemmar.owosmp.personal.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mardroemmar.owosmp.personal.PersonalBehaviorProxy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class PlayerMixin {
  @WrapOperation(
      method = {"dropEquipment", "getBaseExperienceReward"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
  private @NotNull Object onGameRuleGet(
      @NotNull GameRules instance,
      @NotNull GameRule<?> gameRule,
      @NotNull Operation<@NotNull Object> original) {
    if ((Boolean) original.call(instance, gameRule)) {
      return true;
    }
    if (!(((Player) (Object) this) instanceof ServerPlayer player)) {
      return false;
    }
    if (!(player instanceof PersonalBehaviorProxy proxy)) {
      return false;
    }
    return instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.PERSONAL_KEEP_INVENTORY)
        && proxy.owosmp$personalKeepInventoryEnabled();
  }
}
