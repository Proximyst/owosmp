package dev.mardroemmar.owosmp.personal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.mardroemmar.owosmp.gamerule.GameRules;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.gamerules.GameRule;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonalCommands {
  private static final @NotNull Logger LOGGER = LoggerFactory.getLogger(PersonalCommands.class);

  public void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("personal")
            .then(
                this.settingCommand(
                    "pvp",
                    new PersonalSetting(
                        "personal PVP",
                        GameRules.PERSONAL_PVP,
                        PersonalBehaviorProxy::owosmp$personalPvpEnabled,
                        PersonalBehaviorProxy::owosmp$setPersonalPvpEnabled)))
            .then(
                this.settingCommand(
                    "keep_inventory",
                    new PersonalSetting(
                        "personal keep inventory",
                        GameRules.PERSONAL_KEEP_INVENTORY,
                        PersonalBehaviorProxy::owosmp$personalKeepInventoryEnabled,
                        PersonalBehaviorProxy::owosmp$setPersonalKeepInventoryEnabled))));
  }

  private @NotNull ArgumentBuilder<CommandSourceStack, ?> settingCommand(
      @NotNull String name, @NotNull PersonalSetting setting) {
    return Commands.literal(name)
        .requires(
            src -> {
              var lvl = src.getLevel();
              //noinspection ConstantValue // Ran into an NPE here...
              if (lvl == null) {
                return false;
              }
              return lvl.getGameRules().get(setting.gameRule());
            })
        .then(this.statusCommand(setting))
        .then(this.toggleCommand("enable", setting, true))
        .then(this.toggleCommand("disable", setting, false))
        .executes(
            context -> {
              var player = context.getSource().getPlayerOrException();
              if (!(player instanceof PersonalBehaviorProxy proxy)) {
                context.getSource().sendFailure(Component.literal("You cannot use this command."));
                return 0;
              }
              boolean enabled = setting.getter().apply(proxy);
              context
                  .getSource()
                  .sendSuccess(
                      () ->
                          Component.literal(
                              "Your "
                                  + setting.label()
                                  + " is currently "
                                  + (enabled ? "enabled" : "disabled")
                                  + "."),
                      false);
              return 1;
            });
  }

  private @NotNull ArgumentBuilder<CommandSourceStack, ?> statusCommand(
      @NotNull PersonalSetting setting) {
    return Commands.literal("status")
        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
        .then(
            Commands.argument("players", GameProfileArgument.gameProfile())
                .executes(
                    context -> {
                      var players = GameProfileArgument.getGameProfiles(context, "players");
                      for (var player : players) {
                        context
                            .getSource()
                            .sendSuccess(
                                () -> {
                                  var serverPlayer =
                                      context
                                          .getSource()
                                          .getServer()
                                          .getPlayerList()
                                          .getPlayer(player.id());
                                  boolean enabled =
                                      serverPlayer instanceof PersonalBehaviorProxy proxy
                                          && setting.getter().apply(proxy);
                                  return Component.literal(
                                      "Player "
                                          + player.name()
                                          + " has "
                                          + setting.label()
                                          + " "
                                          + (enabled ? "enabled" : "disabled"));
                                },
                                false);
                      }
                      return 1;
                    }));
  }

  private @NotNull ArgumentBuilder<CommandSourceStack, ?> toggleCommand(
      @NotNull String name, @NotNull PersonalSetting setting, boolean enabled) {
    return Commands.literal(name)
        .executes(context -> this.setOwn(context.getSource(), setting, enabled))
        .then(
            Commands.argument("players", GameProfileArgument.gameProfile())
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(
                    context ->
                        this.setPlayers(
                            context.getSource(),
                            setting,
                            GameProfileArgument.getGameProfiles(context, "players"),
                            enabled)));
  }

  private int setOwn(
      @NotNull CommandSourceStack source, @NotNull PersonalSetting setting, boolean enabled)
      throws CommandSyntaxException {
    var player = source.getPlayerOrException();
    if (!(player instanceof PersonalBehaviorProxy proxy)
        || !player.level().getGameRules().get(setting.gameRule())) {
      source.sendFailure(Component.literal("You cannot use this command."));
      return 0;
    }
    setting.setter().accept(proxy, enabled);
    source.sendSuccess(
        () ->
            Component.literal(
                (enabled ? "Enabled" : "Disabled") + " " + setting.label() + " for yourself."),
        true);
    return 1;
  }

  private int setPlayers(
      @NotNull CommandSourceStack source,
      @NotNull PersonalSetting setting,
      @NotNull Collection<NameAndId> players,
      boolean enabled) {
    if (!source.getLevel().getGameRules().get(setting.gameRule())) {
      source.sendFailure(Component.literal("The " + setting.label() + " game rule is disabled."));
      return 0;
    }
    for (var player : players) {
      if (source.getServer().getPlayerList().getPlayer(player.id())
          instanceof PersonalBehaviorProxy proxy) {
        setting.setter().accept(proxy, enabled);
        source.sendSuccess(
            () ->
                Component.literal(
                    (enabled ? "Enabled" : "Disabled")
                        + " "
                        + setting.label()
                        + " for player "
                        + player.name()
                        + "."),
            false);
      } else {
        source.sendFailure(Component.literal("Player " + player.name() + " is not online."));
      }
    }
    return 1;
  }

  /** A per-player boolean setting controlled through a personal command tree. */
  private record PersonalSetting(
      @NotNull String label,
      @NotNull GameRule<Boolean> gameRule,
      @NotNull Function<PersonalBehaviorProxy, Boolean> getter,
      @NotNull BiConsumer<PersonalBehaviorProxy, Boolean> setter) {}
}
