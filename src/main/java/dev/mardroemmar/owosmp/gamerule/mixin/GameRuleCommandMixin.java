package dev.mardroemmar.owosmp.gamerule.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.gamerules.GameRule;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRuleCommand.class)
abstract class GameRuleCommandMixin {
  @Inject(
      method = "setRule",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/gamerules/GameRule;getCommandResult(Ljava/lang/Object;)I"))
  private static void invokeUpdateCallbacks(
      @NotNull CommandContext<CommandSourceStack> context,
      // The generics here are a bit weird, but they're elided, so not a huge deal.
      @NotNull GameRule<? super Object> gameRule,
      @NotNull CallbackInfoReturnable<Integer> cir,
      @Local(name = "value") @NotNull Object value) {
    var callback = GameRules.getUpdateCallback(gameRule.getIdentifier());
    if (callback != null) {
      callback.onUpdate(context.getSource(), gameRule, value);
    }
  }
}
