package dev.mardroemmar.owosmp.llm

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import dev.mardroemmar.owosmp.OwoSmp
import dev.mardroemmar.owosmp.config.ConfigReader
import dev.mardroemmar.owosmp.config.LlmConfig
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.Component

class AiCommands(
    private val clear: () -> Unit,
    private val reload: () -> Unit,
    private val getSession: () -> LlmSession?,
) {
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

    val listModels: (CommandSourceStack) -> Int = { source ->
      val active =
          getSession()?.triggers
              ?: ConfigReader.config.llm?.triggers
              ?: mapOf(LlmSession.DEFAULT_TRIGGER to LlmSession.DEFAULT_MODEL)
      if (active.isEmpty()) {
        source.sendSuccess({ Component.literal("No AI models/triggers configured.") }, false)
      } else {
        val message = buildString {
          append("AI models (${active.size}):\n")
          active.forEach { (trigger, model) -> append(" - $trigger: $model\n") }
        }
            .trimEnd()
        source.sendSuccess({ Component.literal(message) }, false)
      }
      active.size
    }

    listOf("model", "models").forEach { name ->
      val node =
          Commands.literal(name)
              .executes { context -> listModels(context.source) }
              .then(Commands.literal("list").executes { context -> listModels(context.source) })
              .then(
                  Commands.literal("add")
                      .then(
                          Commands.argument("trigger", StringArgumentType.word())
                              .then(
                                  Commands.argument("model", StringArgumentType.greedyString())
                                      .executes { context ->
                                        val trigger =
                                            StringArgumentType.getString(context, "trigger").trim()
                                        val model =
                                            StringArgumentType.getString(context, "model").trim()
                                        if (trigger.endsWith(':') || trigger.endsWith(',')) {
                                          context.source.sendFailure(
                                              Component.literal(
                                                  "Trigger name must not end with ':' or ','."
                                              )
                                          )
                                          return@executes 0
                                        }
                                        perform(context.source, "add model trigger") {
                                          val currentLlm = ConfigReader.config.llm ?: LlmConfig()
                                          val current =
                                              (currentLlm.triggers
                                                      ?: mapOf(
                                                          LlmSession.DEFAULT_TRIGGER to
                                                              LlmSession.DEFAULT_MODEL
                                                      ))
                                                  .toMutableMap()
                                          current[trigger.lowercase(Locale.ROOT)] = model
                                          ConfigReader.save(
                                              ConfigReader.config.copy(
                                                  llm = currentLlm.copy(triggers = current)
                                              )
                                          )
                                          getSession()?.updateTriggers(current)
                                          context.source.sendSuccess(
                                              {
                                                Component.literal(
                                                    "Added AI trigger '$trigger' -> '$model'."
                                                )
                                              },
                                              true,
                                          )
                                          1
                                        }
                                      }
                              )
                      )
              )
      for (rmName in listOf("remove", "rm")) {
        node.then(
            Commands.literal(rmName)
                .then(
                    Commands.argument("trigger", StringArgumentType.word())
                        .suggests { _, builder ->
                          val current =
                              getSession()?.triggers
                                  ?: ConfigReader.config.llm?.triggers
                                  ?: mapOf(LlmSession.DEFAULT_TRIGGER to LlmSession.DEFAULT_MODEL)
                          SharedSuggestionProvider.suggest(current.keys, builder)
                        }
                        .executes { context ->
                          val trigger =
                              StringArgumentType.getString(context, "trigger")
                                  .trim()
                                  .lowercase(Locale.ROOT)
                          perform(context.source, "remove model trigger") {
                            val currentLlm = ConfigReader.config.llm ?: LlmConfig()
                            val current =
                                (currentLlm.triggers
                                        ?: mapOf(
                                            LlmSession.DEFAULT_TRIGGER to LlmSession.DEFAULT_MODEL
                                        ))
                                    .toMutableMap()
                            if (!current.containsKey(trigger)) {
                              context.source.sendFailure(
                                  Component.literal("Trigger '$trigger' is not configured.")
                              )
                              return@perform 0
                            }
                            if (current.size <= 1) {
                              context.source.sendFailure(
                                  Component.literal("Cannot remove the last trigger.")
                              )
                              return@perform 0
                            }
                            current.remove(trigger)
                            ConfigReader.save(
                                ConfigReader.config.copy(llm = currentLlm.copy(triggers = current))
                            )
                            getSession()?.updateTriggers(current)
                            context.source.sendSuccess(
                                { Component.literal("Removed AI trigger '$trigger'.") },
                                true,
                            )
                            1
                          }
                        }
                )
        )
      }
      root.then(node)
    }

    root.then(
        Commands.literal("money").executes { context ->
          val source = context.source
          val llm = ConfigReader.config.llm
          val token = llm?.bearerToken
          if (token.isNullOrBlank()) {
            source.sendFailure(Component.literal("No API token is configured in owosmp.json."))
            return@executes 0
          }
          val endpoint = "${LlmSession.baseUrl(llm.endpoint)}/key"
          source.sendSuccess({ Component.literal("Checking OpenRouter balance...") }, false)
          CompletableFuture.runAsync {
            try {
              val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
              val request =
                  HttpRequest.newBuilder(URI.create(endpoint))
                      .header("Authorization", "Bearer $token")
                      .timeout(Duration.ofSeconds(10))
                      .GET()
                      .build()
              val response = client.send(request, HttpResponse.BodyHandlers.ofString())
              if (response.statusCode() != 200) {
                source.server.execute {
                  source.sendFailure(
                      Component.literal(
                          "OpenRouter API returned HTTP ${response.statusCode()}: ${response.body()}"
                      )
                  )
                }
                return@runAsync
              }
              val rootJson = JsonParser.parseString(response.body()).asJsonObject
              val data =
                  if (rootJson.has("data") && rootJson.get("data").isJsonObject) {
                    rootJson.getAsJsonObject("data")
                  } else {
                    rootJson
                  }

              val limitRemaining =
                  data.get("limit_remaining")?.takeIf { !it.isJsonNull }?.asBigDecimal
              val limit = data.get("limit")?.takeIf { !it.isJsonNull }?.asBigDecimal
              val usage = data.get("usage")?.takeIf { !it.isJsonNull }?.asBigDecimal
              val usageMonthly = data.get("usage_monthly")?.takeIf { !it.isJsonNull }?.asBigDecimal
              val usageDaily = data.get("usage_daily")?.takeIf { !it.isJsonNull }?.asBigDecimal
              val label = data.get("label")?.takeIf { !it.isJsonNull }?.asString

              val message = buildString {
                append("OpenRouter API Key Status:\n")
                append(" - Credit remaining: ${formatMoney(limitRemaining)}\n")
                if (limit != null) {
                  append(" - Limit: ${formatMoney(limit)}\n")
                }
                if (usage != null) {
                  val details =
                      listOfNotNull(
                          usageMonthly?.let { "monthly: ${formatMoney(it)}" },
                          usageDaily?.let { "daily: ${formatMoney(it)}" },
                      )
                  val detailStr =
                      if (details.isNotEmpty()) " (${details.joinToString(", ")})" else ""
                  append(" - Usage: ${formatMoney(usage)}$detailStr\n")
                }
                if (label != null) {
                  append(" - Key label: $label\n")
                }
              }
                  .trimEnd()

              source.server.execute { source.sendSuccess({ Component.literal(message) }, false) }
            } catch (e: Exception) {
              OwoSmp.LOGGER.error("Failed to query OpenRouter key balance", e)
              source.server.execute {
                source.sendFailure(
                    Component.literal("Failed to query OpenRouter balance: ${e.message}")
                )
              }
            }
          }
          1
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
                      ConfigReader.save(
                          ConfigReader.config.copy(
                              aiBannedPlayers = banned.takeIf { it.isNotEmpty() }
                          )
                      )
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

  private fun formatMoney(amount: BigDecimal?): String {
    if (amount == null) return "Unlimited"
    var scaled = amount.stripTrailingZeros()
    if (scaled.scale() < 2) {
      scaled = scaled.setScale(2, RoundingMode.HALF_UP)
    }
    return "$${scaled.toPlainString()} USD"
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
