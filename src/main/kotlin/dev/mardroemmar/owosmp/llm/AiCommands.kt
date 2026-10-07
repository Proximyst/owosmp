package dev.mardroemmar.owosmp.llm

import com.google.gson.JsonParseException
import com.mojang.brigadier.CommandDispatcher
import dev.mardroemmar.owosmp.OwoSmp
import dev.mardroemmar.owosmp.config.ConfigReader
import java.io.IOException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component

class AiCommands(private val clear: () -> Unit, private val reload: () -> Unit) {
  fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
    val root =
        Commands.literal("ai")
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
                Commands.literal("clear").executes { context ->
                  clear()
                  context.source.sendSuccess(
                      { Component.literal("AI history cleared; pending replies discarded.") },
                      true,
                  )
                  1
                }
            )
            .then(
                Commands.literal("reload").executes { context ->
                  perform(context.source, "reload AI config") {
                    reload()
                    context.source.sendSuccess(
                        { Component.literal("AI config reloaded; history cleared.") },
                        true,
                    )
                    1
                  }
                }
            )
    for (ban in listOf(true, false)) {
      root.then(
          Commands.literal(if (ban) "ban" else "unban")
              .then(
                  Commands.argument("players", GameProfileArgument.gameProfile()).executes { context
                    ->
                    val players = GameProfileArgument.getGameProfiles(context, "players")
                    perform(
                        context.source,
                        if (ban) "ban players from AI chat" else "unban players from AI chat",
                    ) {
                      val banned = ConfigReader.config.aiBannedPlayers.orEmpty().toMutableSet()
                      val changed = players.count { player ->
                        if (ban) banned.add(player.id()) else banned.remove(player.id())
                      }
                      ConfigReader.saveBans(banned)
                      context.source.sendSuccess(
                          {
                            Component.literal(
                                if (ban) "Banned $changed player(s) from AI chat."
                                else "Unbanned $changed player(s) from AI chat."
                            )
                          },
                          true,
                      )
                      changed
                    }
                  }
              )
      )
    }
    dispatcher.register(root)
  }

  private fun perform(source: CommandSourceStack, action: String, operation: () -> Int): Int =
      try {
        operation()
      } catch (e: IOException) {
        failure(source, action, e)
      } catch (e: JsonParseException) {
        failure(source, action, e)
      } catch (e: IllegalArgumentException) {
        failure(source, action, e)
      } catch (e: IllegalStateException) {
        failure(source, action, e)
      }

  private fun failure(source: CommandSourceStack, action: String, error: Exception): Int {
    OwoSmp.LOGGER.error("Failed to {}", action, error)
    source.sendFailure(Component.literal("Failed to $action; see the server log for details."))
    return 0
  }
}
