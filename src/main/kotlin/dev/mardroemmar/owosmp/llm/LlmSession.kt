package dev.mardroemmar.owosmp.llm

import com.google.gson.JsonParseException
import com.mojang.serialization.JsonOps
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonNumber
import com.openai.models.chat.completions.ChatCompletionCreateParams
import dev.mardroemmar.owosmp.GameRules
import dev.mardroemmar.owosmp.OwoSmp
import dev.mardroemmar.owosmp.config.LlmConfig
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.network.chat.TextColor
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Shared chat-completions session. Message text is taken from unsigned or decorated content so
 * NoChatReports (broken or stripped signatures) does not NPE on the signed body.
 */
class LlmSession(config: LlmConfig, private val canUse: (UUID?) -> Boolean = { true }) {
  private val maxTurns =
      (config.maxTurns ?: 40).also {
        require(it >= 0) { "llm.maxTurns must be non-negative" }
      }

  private val systemPrompt: String =
      (config.systemPrompt ?: config.prompt)?.trim()?.ifEmpty { null } ?: DEFAULT_SYSTEM_PROMPT

  private val triggers: Map<String, String> = buildMap {
    put(DEFAULT_TRIGGER, DEFAULT_MODEL)
    val configured = config.triggers.orEmpty()
    require(configured.keys.all { it.isNotBlank() }) {
      "llm.triggers must not contain blank triggers"
    }
    require(configured.keys.all { it == it.trim() && !it.endsWith(':') && !it.endsWith(',') }) {
      "llm.triggers keys must be names without surrounding whitespace or trailing colons/commas"
    }
    require(configured.values.all { it.isNotBlank() }) {
      "llm.triggers must not contain blank model IDs"
    }
    val normalized = configured.keys.map { it.lowercase(Locale.ROOT) }
    require(normalized.distinct().size == normalized.size) {
      "llm.triggers must not contain duplicate case-insensitive triggers"
    }
    configured.forEach { (trigger, model) -> put(trigger.lowercase(Locale.ROOT), model) }
  }
      .toList()
      .sortedByDescending { it.first.length }
      .toMap()

  private val prefix: Component =
      config.prefix?.let {
        ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, it).getOrThrow { error ->
          JsonParseException("Invalid llm.prefix: $error")
        }
      }
          ?: Component.empty()
              .append(Component.literal("<"))
              .append(
                  Component.literal("AI").withStyle { it.withColor(TextColor.fromRgb(0x4A6C6F)) }
              )
              .append(Component.literal("> "))

  private val client: OpenAIClient =
      OpenAIOkHttpClient.builder()
          .apiKey(config.bearerToken)
          .baseUrl(baseUrl(config.endpoint))
          .build()

  private val history = AtomicReference(CopyOnWriteArrayList<Turn>())
  private val pricing = ModelPricing()
  @Volatile private var closed = false
  private val executor = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "owosmp-llm").apply { isDaemon = true }
  }

  fun handleChat(message: PlayerChatMessage, sender: ServerPlayer) {
    val server = sender.level().server
    if (server.gameRules.get(GameRules.disableLlm)) return
    val prompt = promptFrom(chatText(message)) ?: return
    if (!canUse(sender.uuid)) {
      sender.sendSystemMessage(Component.literal("You are banned from using AI chat."))
      return
    }
    val speaker = sender.gameProfile.name
    submit(server, speaker, prompt, sender.uuid)
  }

  /**
   * Player chat rewritten into a system message, which is how some NoChatReports setups deliver it.
   */
  fun handleGameMessage(server: MinecraftServer, message: Component) {
    if (server.gameRules.get(GameRules.disableLlm)) return
    val raw = message.string
    val stripped = PLAYER_CHAT_PREFIX.replace(raw, "")
    val prompt = promptFrom(stripped) ?: promptFrom(raw) ?: return
    val speaker = PLAYER_CHAT_PREFIX.find(raw)?.groupValues?.getOrNull(1) ?: "player"
    val sender = server.playerList.getPlayerByName(speaker)
    if (!canUse(sender?.uuid)) {
      if (sender != null) {
        sender.sendSystemMessage(Component.literal("You are banned from using AI chat."))
      } else {
        OwoSmp.LOGGER.warn("Ignored unidentified AI game message while AI bans are active")
      }
      return
    }
    submit(server, speaker, prompt, sender?.uuid)
  }

  fun clearHistory() {
    history.set(CopyOnWriteArrayList())
  }

  fun close() {
    closed = true
    clearHistory()
    executor.shutdownNow()
    client.close()
  }

  private fun promptFrom(raw: String?): Prompt? {
    val text = raw?.trim().orEmpty()
    val trigger =
        triggers.entries.firstOrNull {
          text.startsWith(it.key, ignoreCase = true) &&
              text.getOrNull(it.key.length)?.let { separator ->
                separator.isWhitespace() || separator == ':' || separator == ','
              } == true
        } ?: return null
    val prompt =
        text.substring(trigger.key.length + 1).trim().ifEmpty {
          return null
        }
    return Prompt(prompt, trigger.value)
  }

  private fun submit(server: MinecraftServer, speaker: String, prompt: Prompt, sender: UUID?) {
    val turns = history.get()
    executor.execute {
      if (
          closed ||
              history.get() !== turns ||
              !canUse(sender) ||
              server.gameRules.get(GameRules.disableLlm)
      ) {
        return@execute
      }
      val started = System.nanoTime()
      val reply =
          try {
            complete(speaker, prompt, turns)
          } catch (e: Exception) {
            if (closed || history.get() !== turns) return@execute
            OwoSmp.LOGGER.error("LLM request failed", e)
            Reply(
                "I couldn't answer that just now.",
                Component.literal(
                    "Cost: unavailable\nTokens: unavailable\n" +
                        "Requested model: ${prompt.model}\nLatency: ${(System.nanoTime() - started) / 1_000_000} ms\nRequest failed"
                ),
            )
          }
      server.execute {
        if (
            !closed &&
                history.get() === turns &&
                canUse(sender) &&
                !server.gameRules.get(GameRules.disableLlm)
        ) {
          broadcast(server, reply)
        }
      }
    }
  }

  private fun complete(speaker: String, prompt: Prompt, turns: CopyOnWriteArrayList<Turn>): Reply {
    val userText = "$speaker: ${prompt.text}"
    val previousTurns = turns.toList()
    val params =
        ChatCompletionCreateParams.builder()
            .model(prompt.model)
            .addSystemMessage(systemPrompt)
            .apply {
              previousTurns.forEach { turn ->
                addUserMessage(turn.user)
                addAssistantMessage(turn.assistant)
              }
            }
            .addUserMessage(userText)
            .build()

    val started = System.nanoTime()
    val completion = client.chat().completions().create(params)
    val latencyMs = (System.nanoTime() - started) / 1_000_000
    val reply =
        completion
            .choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            .orEmpty()
            .let { EMOJIS.replace(it, "").trim() }
            .ifBlank { "I don't have a reply for that." }

    turns.add(Turn(userText, reply))
    while (turns.size > maxTurns) {
      turns.removeAt(0)
    }
    val usage = completion.usage().orElse(null)
    val cost = usage?._additionalProperties()?.get("cost")
    val inputTokens =
        usage?.promptTokens()
            ?: estimateTokens(
                systemPrompt + previousTurns.joinToString("") { it.user + it.assistant } + userText
            )
    val outputTokens =
        usage?.completionTokens()
            ?: estimateTokens(
                completion.choices().firstOrNull()?.message()?.content()?.orElse(null).orEmpty()
            )
    val parsedCost =
        (cost as? JsonNumber)?.value?.let { value ->
          runCatching {
            when (value) {
              is BigDecimal -> value
              else -> BigDecimal(value.toString())
            }
          }
              .getOrNull()
        }
    val estimatedCost =
        if (parsedCost != null) null
        else
            pricing.estimate(completion.model(), inputTokens, outputTokens)
                ?: if (completion.model() != prompt.model) {
                  pricing.estimate(prompt.model, inputTokens, outputTokens)
                } else null
    val costText =
        parsedCost?.let { "${formatCost(it)} USD" }
            ?: estimatedCost?.let { "~${formatCost(it)} USD (estimated)" }
            ?: "unavailable (no model pricing)"
    val tokensText =
        usage?.let {
          "${it.totalTokens()} (${it.promptTokens()} input, ${it.completionTokens()} output)"
        }
            ?: "~${inputTokens + outputTokens} (~$inputTokens input, ~$outputTokens output; estimated)"
    val hover =
        Component.literal(
            "Cost: $costText\nTokens: $tokensText\nModel: ${completion.model()}\nLatency: $latencyMs ms"
        )
    return Reply(reply, hover)
  }

  private data class Turn(val user: String, val assistant: String)

  private data class Prompt(val text: String, val model: String)

  private data class Reply(val text: String, val hover: Component)

  private fun broadcast(server: MinecraftServer, reply: Reply) {
    for (chunk in chunk(reply.text)) {
      val message =
          Component.empty()
              .append(prefix)
              .append(
                  Component.literal(chunk).withStyle {
                    it.withHoverEvent(HoverEvent.ShowText(reply.hover))
                  }
              )
              .withStyle { it.withHoverEvent(HoverEvent.ShowText(reply.hover)) }
      server.playerList.broadcastSystemMessage(message, false)
    }
  }

  companion object {
    const val DEFAULT_TRIGGER = "ai"
    const val DEFAULT_MODEL = "mistralai/mistral-nemo"

    private fun formatCost(amount: BigDecimal): String {
      val scaled = if (amount.scale() > 8) amount.setScale(8, RoundingMode.HALF_UP) else amount
      val plain = scaled.stripTrailingZeros().toPlainString()
      return if (plain.contains('.')) plain else "$plain.00"
    }

    private fun estimateTokens(text: String): Long =
        (text.codePointCount(0, text.length).toLong() + 3) / 4

    private const val DEFAULT_SYSTEM_PROMPT =
        """
        You are a chatter in a Minecraft SMP chat. Write largely in lower-case, like a Minecraft player would.
        You're interacting with adults, all 18+. Do not censor your language.
        Join in on playful banter and roleplay. You are not a moderator or admin.
        When other adults make sexual innuendos, respond with witty double entendres and light, cheeky flirting.
        Keep it suggestive rather than explicit: no graphic sexual descriptions. Don't force innuendo into unrelated conversations.
        Respect anyone who declines flirting or asks you to stop.
        You prefer to say 'shalom' to greet people.
        Do not use markdown, do not use emojis.
      """

    private val PLAYER_CHAT_PREFIX = Regex("""^<([^>]{1,64})>\s+""")

    // Keep plain digits, # and *; remove keycaps and emoji sequence formatting with the emoji.
    private val EMOJIS =
        Regex(
            """[#*0-9]\uFE0F?\u20E3|\u200D?[\p{IsEmoji}&&[^#*0-9]][\uFE0E\uFE0F\x{E0020}-\x{E007F}]*"""
        )

    /**
     * Prefer unsigned content. NoChatReports often nulls the signature and sometimes the signed
     * body, and [PlayerChatMessage.signedContent] NPEs in that case.
     */
    fun chatText(message: PlayerChatMessage): String? {
      val unsigned = runCatching { message.unsignedContent()?.string }.getOrNull()
      if (!unsigned.isNullOrBlank()) {
        return unsigned
      }
      val decorated = runCatching { message.decoratedContent().string }.getOrNull()
      if (!decorated.isNullOrBlank()) {
        return decorated
      }
      return runCatching { message.signedContent() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun baseUrl(endpoint: String): String =
        endpoint.trim().trimEnd('/').removeSuffix("/chat/completions")

    private fun chunk(text: String, limit: Int = 220): List<String> {
      if (text.length <= limit) {
        return listOf(text)
      }
      val parts = mutableListOf<String>()
      var rest = text
      while (rest.length > limit) {
        val breakAt = rest.lastIndexOf(' ', limit).takeIf { it > 40 } ?: limit
        parts.add(rest.substring(0, breakAt).trim())
        rest = rest.substring(breakAt).trim()
      }
      if (rest.isNotEmpty()) {
        parts.add(rest)
      }
      return parts
    }
  }
}
