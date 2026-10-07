package dev.mardroemmar.owosmp

import dev.mardroemmar.owosmp.config.ConfigReader
import dev.mardroemmar.owosmp.config.OwoSmpConfig
import dev.mardroemmar.owosmp.llm.AiCommands
import dev.mardroemmar.owosmp.llm.LlmSession
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class OwoSmp : ModInitializer {
  @Volatile private var session: LlmSession? = null

  override fun onInitialize() {
    GameRules.load()
    ConfigReader.load()
    session = createSession(ConfigReader.config)
    setupLlm()
    val commands = AiCommands({ session?.clearHistory() }, ::reloadLlm) { session }
    CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
      commands.register(dispatcher)
      OwoCommands.register(dispatcher, ::reload)
    }
    ServerLifecycleEvents.SERVER_STOPPING.register {
      session?.close()
      session = null
    }
  }

  private fun createSession(config: OwoSmpConfig): LlmSession? {
    val llm = config.llm
    val token = llm?.bearerToken
    if (llm == null || token.isNullOrBlank()) {
      LOGGER.info("LLM chat is disabled; set llm.bearerToken in owosmp.json to enable it")
      return null
    }

    val session =
        LlmSession(llm) { player ->
          val banned = ConfigReader.config.aiBannedPlayers.orEmpty()
          banned.isEmpty() || (player != null && player !in banned)
        }
    LOGGER.info("LLM chat session ready")
    return session
  }

  fun reload() {
    reloadLlm()
  }

  private fun reloadLlm() {
    val config = ConfigReader.read()
    val replacement = createSession(config)
    val previous = session
    ConfigReader.config = config
    session = replacement
    previous?.close()
  }

  private fun setupLlm() {
    // Do not verify or read the signature. NoChatReports delivers chat with a missing or
    // invalid signature, and signed content may be unusable.
    ServerMessageEvents.CHAT_MESSAGE.register { message, player, _ ->
      session?.handleChat(message, player)
    }
    ServerMessageEvents.GAME_MESSAGE.register { server, message, overlay ->
      if (!overlay) {
        session?.handleGameMessage(server, message)
      }
    }
  }

  companion object {
    const val MOD_ID = "owosmp"
    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)
  }
}
