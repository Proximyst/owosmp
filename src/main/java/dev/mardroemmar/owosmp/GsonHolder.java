package dev.mardroemmar.owosmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapterFactory;
import java.util.ServiceLoader;
import org.jetbrains.annotations.NotNull;

public class GsonHolder {
  private static final Gson GSON = createGson();

  private GsonHolder() {}

  private static @NotNull Gson createGson() {
    GsonBuilder gsonBuilder = new GsonBuilder().disableJdkUnsafe().setPrettyPrinting();
    for (TypeAdapterFactory factory : ServiceLoader.load(TypeAdapterFactory.class)) {
      gsonBuilder.registerTypeAdapterFactory(factory);
    }
    return gsonBuilder.create();
  }

  public static @NotNull Gson gson() {
    return GSON;
  }
}
