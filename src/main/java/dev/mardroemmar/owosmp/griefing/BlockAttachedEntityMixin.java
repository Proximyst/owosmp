package dev.mardroemmar.owosmp.griefing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockAttachedEntity.class)
abstract class BlockAttachedEntityMixin {
  @WrapOperation(
      method = "hurtServer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
  private @NotNull Object onGameRuleGet(
      @NotNull GameRules instance,
      @NotNull GameRule<?> gameRule,
      Operation<@NotNull Object> original) {
    if (gameRule.getIdentifier().equals(GameRules.MOB_GRIEFING.getIdentifier())) {
      if (!(Boolean) original.call(instance, gameRule)) {
        return false;
      }
      return switch ((Entity) (Object) this) {
        case Cushion _ ->
            instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.CUSHION_DIE_TO_MOBS);
        case GlowItemFrame _ ->
            instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.ITEM_FRAME_GLOW_DIE_TO_MOBS);
        case ItemFrame _ ->
            instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.ITEM_FRAME_DIE_TO_MOBS);
        case LeashFenceKnotEntity _ ->
            instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.LEASH_FENCE_KNOT_DIE_TO_MOBS);
        case Painting _ ->
            instance.get(dev.mardroemmar.owosmp.gamerule.GameRules.PAINTING_DIE_TO_MOBS);
        default -> true;
      };
    }
    return original.call(instance, gameRule);
  }
}
