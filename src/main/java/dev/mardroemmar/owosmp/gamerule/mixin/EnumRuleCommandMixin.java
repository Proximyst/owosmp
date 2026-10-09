package dev.mardroemmar.owosmp.gamerule.mixin;

import com.mojang.brigadier.context.CommandContext;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import net.fabricmc.fabric.impl.gamerule.EnumRuleCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.gamerules.GameRule;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnumRuleCommand.class)
abstract class EnumRuleCommandMixin {
  // The generics here are a bit weird, but they're elided, so not a huge deal.
  @SuppressWarnings({"rawtypes", "unchecked"})
  @Inject(
      method = "executeAndSetEnum",
      at =
          @At(
              value = "INVOKE",
              shift = At.Shift.AFTER,
              target =
                  "Lnet/minecraft/commands/CommandSourceStack;sendSuccess(Ljava/util/function/Supplier;Z)V"))
  private static <E extends Enum<E>> void onGameRuleUpdate(
      @NotNull CommandContext<CommandSourceStack> context,
      @NotNull E value,
      @NotNull GameRule<E> enumRule,
      @NotNull CallbackInfoReturnable<Integer> cir) {
    var callback = GameRules.getUpdateCallback(enumRule.getIdentifier());
    if (callback != null) {
      callback.onUpdate(context.getSource(), enumRule, value);
    }
  }
}
