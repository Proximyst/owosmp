package dev.mardroemmar.owosmp

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object OwoCommands {
  fun register(dispatcher: CommandDispatcher<CommandSourceStack>, reloadAll: () -> Unit) {
    dispatcher.register(
        Commands.literal("owo")
            .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
            .then(
                Commands.literal("reload").executes { context ->
                  try {
                    reloadAll()
                    context.source.sendSuccess(
                        { Component.literal("OwoSMP config reloaded.") },
                        true,
                    )
                    1
                  } catch (e: Exception) {
                    OwoSmp.LOGGER.error("Failed to reload OwoSMP config", e)
                    context.source.sendFailure(
                        Component.literal(
                            "Failed to reload config: ${e.message ?: "see server logs"}"
                        )
                    )
                    0
                  }
                }
            )
    )
  }
}
