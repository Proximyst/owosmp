package dev.mardroemmar.owosmp.config

import java.util.UUID

data class OwoSmpConfig(
    val llm: LlmConfig? = null,
    /** Player UUIDs blocked from AI chat, maintained by /ai ban and /ai unban. */
    val aiBannedPlayers: Set<UUID>? = null,
)
