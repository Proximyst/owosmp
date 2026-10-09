package dev.mardroemmar.owosmp.villager;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
abstract class VillagerMixin {
  @ModifyReturnValue(method = "getPlayerReputation", at = @At("RETURN"))
  private int getPlayerReputationAlsoReturnGlobalGossip(int original) {
    var self = (Villager) (Object) this;
    if (!(self.level() instanceof ServerLevel sl)
        || !sl.getGameRules().get(GameRules.GLOBAL_VILLAGER_CURE_REPUTATION)) {
      return original;
    }
    if (original < 0) {
      // You've done some kind of crime.
      return original;
    }
    return Math.max(original, self.getGossips().getReputation(new UUID(0, 0), _ -> true));
  }

  @Inject(
      method = "onReputationEventFrom",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/ai/gossip/GossipContainer;add(Ljava/util/UUID;Lnet/minecraft/world/entity/ai/gossip/GossipType;I)V",
              shift = At.Shift.AFTER))
  private void onCuredZombieVillagerReputation(
      @NotNull ReputationEventType type, @NotNull Entity source, @NotNull CallbackInfo ci) {
    var self = (Villager) (Object) this;
    if (!(self.level() instanceof ServerLevel sl)
        || !sl.getGameRules().get(GameRules.GLOBAL_VILLAGER_CURE_REPUTATION)) {
      return;
    }
    if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
      self.getGossips().add(new UUID(0, 0), GossipType.MAJOR_POSITIVE, 20);
      // We intentionally don't apply MINOR_POSITIVE here. This gives curing an edge, even if minor.
    }
  }
}
