package dev.mardroemmar.owosmp.llm;

import com.google.gson.reflect.TypeToken;
import dev.mardroemmar.owosmp.GsonHolder;
import dev.mardroemmar.owosmp.immutable.KeepAnnotations;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KeepAnnotations
@Gson.TypeAdapters
@Value.Immutable
interface ModelPricingResponse {
  @NotNull
  List<@NotNull Model> data();

  @Nullable
  Links links();

  @Value.Immutable
  interface Model {
    @NotNull
    String id();

    @NotNull
    Pricing pricing();
  }

  @Value.Immutable
  interface Pricing {
    @NotNull
    BigDecimal prompt();

    @NotNull
    BigDecimal completion();
  }

  @Value.Immutable
  interface Links {
    @Nullable
    String next();
  }
}

public class ModelPricing {
  private static final Logger LOGGER = LoggerFactory.getLogger(ModelPricing.class);
  private static final URI PRICING_URI = URI.create("https://openrouter.ai/api/v1/models");
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RETRY_DELAY = Duration.ofMinutes(5);
  private static final Duration REFRESH_DELAY = Duration.ofHours(1);
  private final @NotNull HttpClient client;

  /** The current model prices, or {@code null} if not yet fetched. */
  private @Nullable Map<String, Price> prices;

  /** When the next refresh should happen, compared to {@link System#nanoTime()}. */
  private long nextRefresh;

  public ModelPricing() {
    this.client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
  }

  public @Nullable BigDecimal estimatePrice(
      @NotNull String model, long inputTokens, long outputTokens) {
    long now = System.nanoTime();
    if (now >= this.nextRefresh) {
      this.nextRefresh = now + RETRY_DELAY.toNanos();
      var refreshedPrices = new HashMap<String, Price>();
      if (this.updatePrices(refreshedPrices)) {
        this.prices = Collections.unmodifiableMap(refreshedPrices);
        this.nextRefresh = System.nanoTime() + REFRESH_DELAY.toNanos();
      }
    }

    var price = this.prices == null ? null : this.prices.get(model);
    if (price == null) {
      return null;
    }
    return price
        .inputPrice()
        .multiply(BigDecimal.valueOf(inputTokens))
        .add(price.outputPrice().multiply(BigDecimal.valueOf(outputTokens)));
  }

  private boolean updatePrices(@NotNull Map<String, Price> prices) {
    // TODO: Configurable pricing API.
    var requestUri = PRICING_URI;
    while (true) {
      var request =
          HttpRequest.newBuilder(requestUri)
              .header("Accept", "application/json")
              .timeout(REQUEST_TIMEOUT)
              .GET()
              .build();
      HttpResponse<String> response;
      try {
        response = this.client.send(request, BodyHandlers.ofString());
      } catch (IOException ex) {
        LOGGER.error("Failed to fetch model pricing from OpenRouter", ex);
        return false;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        LOGGER.error("Interrupted while fetching model pricing from OpenRouter", ex);
        return false;
      }

      if (response.statusCode() != 200) {
        LOGGER.error(
            "Failed to fetch model pricing from OpenRouter: HTTP {}", response.statusCode());
        return false;
      }

      ModelPricingResponse page;
      try {
        page = GsonHolder.gson().fromJson(response.body(), new TypeToken<>() {});
      } catch (RuntimeException ex) {
        LOGGER.error("Invalid model pricing response from OpenRouter", ex);
        return false;
      }
      if (page == null) {
        LOGGER.error("Invalid empty model pricing response from OpenRouter");
        return false;
      }
      for (ModelPricingResponse.Model model : page.data()) {
        ModelPricingResponse.Pricing pricing = model.pricing();
        if (pricing.prompt().signum() >= 0 && pricing.completion().signum() >= 0) {
          prices.put(
              model.id(),
              ImmutablePrice.builder()
                  .inputPrice(pricing.prompt())
                  .outputPrice(pricing.completion())
                  .build());
        }
      }

      var links = page.links();
      var next = links == null ? null : links.next();
      if (next == null || next.isBlank()) {
        return true;
      }
      try {
        requestUri = response.uri().resolve(next);
      } catch (IllegalArgumentException ex) {
        LOGGER.error("Invalid next model-pricing page URI from OpenRouter: {}", next, ex);
        return false;
      }
    }
  }

  @KeepAnnotations
  @Value.Immutable
  public interface Price {
    @NotNull
    BigDecimal inputPrice();

    @NotNull
    BigDecimal outputPrice();
  }
}
