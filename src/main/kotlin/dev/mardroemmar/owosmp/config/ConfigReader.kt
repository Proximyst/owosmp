package dev.mardroemmar.owosmp.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import dev.mardroemmar.owosmp.OwoSmp
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import net.fabricmc.loader.api.FabricLoader

object ConfigReader {
  @Volatile
  lateinit var config: OwoSmpConfig
    internal set

  fun load() {
    config = read()
  }

  fun read(): OwoSmpConfig {
    val configDir: Path = FabricLoader.getInstance().configDir
    val configPath = configDir.resolve("owosmp.json")

    val defaultConfig = OwoSmpConfig()
    val gson = GsonBuilder().setPrettyPrinting().create()
    return if (!Files.exists(configPath)) {
      try {
        val json = gson.toJson(defaultConfig)
        Files.writeString(configPath, json)
        OwoSmp.LOGGER.info("Created default config at {}", configPath)
        defaultConfig
      } catch (e: Exception) {
        OwoSmp.LOGGER.error("Failed to create default config file", e)
        defaultConfig
      }
    } else {
      val json = Files.readString(configPath)
      gson.fromJson<OwoSmpConfig?>(json) ?: throw JsonParseException("Config must not be null")
    }
  }

  fun saveBans(bannedPlayers: Set<UUID>) {
    val path = FabricLoader.getInstance().configDir.resolve("owosmp.json")
    val gson = GsonBuilder().setPrettyPrinting().create()
    val json = JsonParser.parseString(Files.readString(path)).asJsonObject
    json.add("aiBannedPlayers", gson.toJsonTree(bannedPlayers))
    val temporary = Files.createTempFile(path.parent, "owosmp-", ".json")
    try {
      Files.writeString(temporary, gson.toJson(json))
      try {
        Files.move(
            temporary,
            path,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
      } catch (e: AtomicMoveNotSupportedException) {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
      }
      config = config.copy(aiBannedPlayers = bannedPlayers)
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
}

inline fun <reified T> Gson.fromJson(json: String): T =
    this.fromJson(json, object : TypeToken<T>() {})
