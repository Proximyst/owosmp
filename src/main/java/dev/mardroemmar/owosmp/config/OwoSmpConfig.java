package dev.mardroemmar.owosmp.config;

import com.google.gson.annotations.SerializedName;
import dev.mardroemmar.owosmp.immutable.KeepAnnotations;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;

@KeepAnnotations
@Value.Immutable
@Gson.TypeAdapters
public interface OwoSmpConfig {
  @SerializedName("llm")
  @Nullable
  LlmConfig llm();
}
