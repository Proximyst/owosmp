package dev.mardroemmar.owosmp.config;

import com.google.gson.reflect.TypeToken;
import dev.mardroemmar.owosmp.GsonHolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigHelper {
  private static final @NotNull AtomicReference<@Nullable OwoSmpConfig> CONFIG =
      new AtomicReference<>();

  private ConfigHelper() {}

  /** The path to the configuration file. */
  private static @NotNull Path configPath() {
    return FabricLoader.getInstance().getConfigDir().resolve("owosmp.json");
  }

  /** Gets the current configuration state from cache, or disk if first read. */
  public static @NotNull OwoSmpConfig getConfig() {
    if (CONFIG.get() == null) {
      try {
        CONFIG.set(readConfig());
      } catch (IOException e) {
        throw new RuntimeException("Failed to read config", e);
      }
    }
    return Objects.requireNonNull(CONFIG.get(), "must never be set back to null");
  }

  /** Like {@link #getConfig()} but reloads the config from disk. */
  public static @NotNull OwoSmpConfig reloadConfig() {
    try {
      var newConfig = readConfig();
      CONFIG.set(newConfig);
      return newConfig;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read config", e);
    }
  }

  /** Tries to read the configuration file from disk. Default if not exists. */
  private static @NotNull OwoSmpConfig readConfig() throws IOException {
    var path = configPath();
    if (!path.toFile().exists()) {
      var defaultConfig = ImmutableOwoSmpConfig.builder().build();
      writeConfig(defaultConfig);
      return defaultConfig;
    }

    try (var reader = Files.newBufferedReader(path)) {
      return GsonHolder.gson().fromJson(reader, new TypeToken<>() {});
    }
  }

  /** Tries to write the configuration file to disk and cache. */
  public static void writeConfig(@NotNull OwoSmpConfig config) throws IOException {
    CONFIG.set(config);
    var path = configPath();
    try (var writer = Files.newBufferedWriter(path)) {
      GsonHolder.gson().toJson(config, writer);
    }
  }
}
