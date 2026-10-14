package dev.alllexey.itmowidgets.feature.sport.domain.model

import kotlin.time.Instant

/**
 * A section (DECISIONS.md, Спорт, «Секция»): the slot of a booked [lesson], the same lesson every two weeks, which
 * Backend queues for every occurrence. [place] of [subscribers] is the user's place in the slot's section queue.
 */
data class MySportSection(
    val id: Long,
    val lesson: SportQueueLesson,
    val dates: List<SportSectionDate>,
    val place: Int,
    val subscribers: Int,
)

/** A predicted occurrence; [state] is null until auto-sign for it opens and the user did not turn it off. */
data class SportSectionDate(val start: Instant, val end: Instant, val state: SportSectionDateState?, val entryId: Long?)

enum class SportSectionDateState {
    /** In the auto-sign queue. */
    QUEUED,

    /** Skipped: the monthly auto-sign limit had no free slot. */
    LIMIT,

    /** The user turned this date off. */
    SKIPPED,
}

/** A lesson's slot: counts and the user's own [place] only; [full] when the user can no longer subscribe. */
data class SportSectionSlot(
    val lessonId: Long,
    val subscribers: Int,
    val cap: Int?,
    val full: Boolean,
    val subscriptionId: Long?,
    val place: Int?,
)
