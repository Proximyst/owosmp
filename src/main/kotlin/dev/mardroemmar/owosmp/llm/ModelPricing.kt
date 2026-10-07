package dev.mardroemmar.owosmp.llm

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import dev.mardroemmar.owosmp.OwoSmp
import java.io.IOException
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Accessed only by the session's single request worker. Prices are USD per token. */
internal class ModelPricing {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
  private var prices = emptyMap<String, Price>()
  private var refreshAt = 0L

  fun estimate(model: String, inputTokens: Long, outputTokens: Long): BigDecimal? {
    if (System.nanoTime() >= refreshAt) {
      refreshAt = System.nanoTime() + Duration.ofMinutes(5).toNanos()
      try {
        val request =
            HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/models"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
          OwoSmp.LOGGER.warn("Could not refresh AI model prices: HTTP {}", response.statusCode())
        } else {
          val root = JsonParser.parseString(response.body())
          val data =
              root.asJsonObject.getAsJsonArray("data")
                  ?: throw JsonParseException("Model pricing response has no data")
          prices = buildMap {
            for (entry in data) {
              val modelData = entry.asJsonObject
              val id =
                  modelData.get("id")?.asString
                      ?: throw JsonParseException("Model pricing entry has no ID")
              val pricing = modelData.getAsJsonObject("pricing") ?: continue
              val input = pricing.get("prompt")?.asString?.toBigDecimalOrNull()
              val output = pricing.get("completion")?.asString?.toBigDecimalOrNull()
              if (input != null && output != null && input.signum() >= 0 && output.signum() >= 0) {
                put(id, Price(input, output))
              }
            }
          }
          refreshAt = System.nanoTime() + Duration.ofHours(1).toNanos()
        }
      } catch (e: IOException) {
        OwoSmp.LOGGER.warn("Could not refresh AI model prices", e)
      } catch (e: JsonParseException) {
        OwoSmp.LOGGER.warn("Invalid AI model pricing response", e)
      } catch (e: IllegalStateException) {
        OwoSmp.LOGGER.warn("Invalid AI model pricing response", e)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
      }
    }
    return prices[model]?.let {
      it.input
          .multiply(BigDecimal.valueOf(inputTokens))
          .add(it.output.multiply(BigDecimal.valueOf(outputTokens)))
    }
  }

  private data class Price(val input: BigDecimal, val output: BigDecimal)
}
