package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Makes a section of the slot of a lesson the viewer is booked on: the same lesson every two weeks. */
@Serializable
data class SportSectionRequest(
    val lessonId: Long,
)

/** Turns one predicted occurrence of a section off ([skipped] true) or back on. */
@Serializable
data class SportSectionOccurrenceRequest(
    @Serializable(with = WireInstantSerializer::class) val start: Instant,
    val skipped: Boolean,
)

/** The viewer's own section: a slot of the same lesson every two weeks. */
@Serializable
data class MySportSection(
    val id: Long,
    /** The booked lesson the section was made from: the slot's section, teacher, place, weekday and time. */
    val lesson: SportLessonDto,
    /** The next predicted occurrences, earliest first. */
    val occurrences: List<SportSectionDate>,
    /** The viewer's place in the slot's section queue, from 1. */
    val place: Int,
    /** Everyone in the slot's section queue, the viewer included. */
    val subscribers: Int,
    @Serializable(with = WireInstantSerializer::class) val createdAt: Instant,
)

/** One predicted occurrence of a section. */
@Serializable
data class SportSectionDate(
    @Serializable(with = WireInstantSerializer::class) val start: Instant,
    @Serializable(with = WireInstantSerializer::class) val end: Instant,
    /** Null until auto-sign for this date opens and the viewer did not turn it off. */
    val state: SportSectionOccurrenceState?,
    /** The auto-sign entry of a queued occurrence. */
    val entryId: Long?,
    /** The entry's place in the lesson's auto-sign queue, in the order attempts follow, while it waits. */
    val position: Int? = null,
    val total: Int? = null,
)

/**
 * A handled occurrence. Display enum: a value added by a newer server decodes as [UNKNOWN], which consumers map
 * explicitly.
 */
@Serializable(with = SportSectionOccurrenceStateSerializer::class)
enum class SportSectionOccurrenceState {
    /** In the auto-sign queue; `entryId` names the entry. */
    QUEUED,

    /** Skipped: the monthly auto-sign limit had no free slot when the date opened. */
    LIMIT,

    /** The viewer turned this date off. */
    SKIPPED,

    /** Decode-only: a state this client does not know. Never sent by Backend and never encoded. */
    UNKNOWN,
}

internal object SportSectionOccurrenceStateSerializer :
    UnknownTolerantEnumSerializer<SportSectionOccurrenceState>(
        "SportSectionOccurrenceState",
        SportSectionOccurrenceState.entries,
        SportSectionOccurrenceState.UNKNOWN,
    )

/** A lesson's slot as the viewer sees it: counts and the viewer's own place, never other subscribers. */
@Serializable
data class SportSectionSlot(
    val lessonId: Long,
    val subscribers: Int,
    /** How many subscribers the slot admits; null while the lesson's places are unknown. */
    val cap: Int?,
    /** The viewer cannot subscribe: the cap is reached. */
    val full: Boolean,
    /** The viewer's own subscription to this slot. */
    val subscriptionId: Long?,
    val place: Int?,
)
