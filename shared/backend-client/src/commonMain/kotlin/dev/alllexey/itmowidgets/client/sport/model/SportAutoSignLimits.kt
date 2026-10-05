package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** How many auto-sign entries the user may hold ([limit]), how many are free now, and when the next one frees up. */
@Serializable
data class SportAutoSignLimits(
    val limit: Int,
    val available: Int,
    @Serializable(with = WireInstantSerializer::class) val nextAvailableAt: Instant,
)
