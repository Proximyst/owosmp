package dev.mardroemmar.owosmp.config

import com.google.gson.JsonElement

data class LlmConfig(
    val endpoint: String = "https://openrouter.ai/api/v1/chat/completions",
    val bearerToken: String? = null,
    val model: String = "mistralai/mistral-nemo",
    /** Minecraft Component JSON. Null uses "<AI> " with AI coloured #4A6C6F. */
    val prefix: JsonElement? = null,
    /**
     * Trigger name to model ID, e.g. "mimo". Matches a following space, colon or comma. Overrides
     * "ai".
     */
    val triggers: Map<String, String>? = null,
    /** Retained user/reply pairs. Omitted or null uses 40; zero disables history. */
    val maxTurns: Int? = 40,
    /** System prompt. Null or blank uses the default SMP chat prompt. */
    val systemPrompt: String? = null,
    val prompt: String? = null,
)
