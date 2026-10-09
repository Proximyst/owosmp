package dev.mardroemmar.owosmp.mixin.dragonegg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mardroemmar.owosmp.GameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enables respawning the dragon egg on death, when {@link GameRules#RESPAWN_DRAGON_EGG} is set to
 * true.
 */
@Mixin(EnderDragonFight.class)
abstract class EnderDragonFightMixin {
  @Shadow @NotNull private ServerLevel level;

  /** Decides whether the dragon egg should respawn. */
  @WrapOperation(
      method = "setDragonKilled",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;hasPreviouslyKilledDragon:Z",
              opcode = Opcodes.GETFIELD))
  private boolean shouldDragonEggRespawn(
      @NotNull EnderDragonFight instance, @NotNull Operation<@NotNull Boolean> original) {
    boolean hasPreviouslyKilledDragon = original.call(instance);
    // Note: the caller negates our answer, so we'll need to do the same.
    boolean shouldRespawnEgg =
        !hasPreviouslyKilledDragon || level.getGameRules().get(GameRules.RESPAWN_DRAGON_EGG);
    return !shouldRespawnEgg;
  }
}
