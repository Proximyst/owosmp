package dev.mardroemmar.owosmp.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import dev.mardroemmar.owosmp.OwoSmp
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import net.fabricmc.loader.api.FabricLoader

object ConfigReader {
  @Volatile
  lateinit var config: OwoSmpConfig
    internal set

  private val configPath: Path
    get() = FabricLoader.getInstance().configDir.resolve("owosmp.json")

  private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

  fun load() {
    config = read()
  }

  fun read(): OwoSmpConfig {
    val path = configPath
    val defaultConfig = OwoSmpConfig()
    return if (!Files.exists(path)) {
      try {
        val json = gson.toJson(defaultConfig)
        Files.writeString(path, json)
        OwoSmp.LOGGER.info("Created default config at {}", path)
        defaultConfig
      } catch (e: Exception) {
        OwoSmp.LOGGER.error("Failed to create default config file", e)
        defaultConfig
      }
    } else {
      val json = Files.readString(path)
      gson.fromJson<OwoSmpConfig?>(json) ?: throw JsonParseException("Config must not be null")
    }
  }

  fun save(newConfig: OwoSmpConfig) {
    val path = configPath
    val temporary = Files.createTempFile(path.parent, "owosmp-", ".json")
    try {
      Files.writeString(temporary, gson.toJson(newConfig))
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
      config = newConfig
    } finally {
      Files.deleteIfExists(temporary)
    }
  }
}

inline fun <reified T> Gson.fromJson(json: String): T =
    this.fromJson(json, object : TypeToken<T>() {})
