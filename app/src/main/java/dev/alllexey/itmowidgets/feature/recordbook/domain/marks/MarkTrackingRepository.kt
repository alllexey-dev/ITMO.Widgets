package dev.alllexey.itmowidgets.feature.recordbook.domain.marks

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.coroutines.flow.Flow

/** How a check of one source ended once it had an answer. */
sealed interface MarkCheckResult {
    /** The first answer of this source, half-year or switch: only a snapshot. */
    data object Baseline : MarkCheckResult

    /** [found] subjects got new or changed marks. */
    data class Compared(val found: Int) : MarkCheckResult

    /** A newer answer or a reset was written meanwhile; this one was dropped. */
    data object Stale : MarkCheckResult
}

/** How a background check of BARS ended. */
sealed interface BarsCheck {
    data class Done(val result: MarkCheckResult) : BarsCheck
    data object NoSession : BarsCheck
    data object SessionEnded : BarsCheck
    data class Failed(val error: AppError) : BarsCheck
}

/** Wall-clock moment a screen started asking for marks. */
@JvmInline
value class ReadStamp(val millis: Long)

/** Snapshots of both sources and the unread subjects, on the device only. */
interface MarkTrackingRepository {
    /** Unread subjects of the last 30 days, newest first; the file is read on the first collection. */
    fun observeNews(): Flow<List<MarkNews>>

    /** Reads My ITMO's recordbook of the current half-year and compares it with the snapshot. */
    suspend fun checkMyItmo(): AppResult<MarkCheckResult>

    /** Reads the BARS journals of the current half-year in the background, renewing through ITMO.ID cookies. */
    suspend fun checkBars(): BarsCheck

    /** Taken before a screen asks for marks; a later snapshot write makes that answer stale. */
    fun readStarted(): ReadStamp

    /** A list the app showed advances an existing snapshot; what changed is marked unread but not notified. */
    suspend fun recordMyItmoSeen(
        stamp: ReadStamp,
        half: StudyHalf,
        programId: Long,
        semester: Int,
        subjects: List<RecordbookSubject>
    )

    suspend fun recordBarsSeen(stamp: ReadStamp, half: StudyHalf, plans: List<BarsPlanMarks>)

    /** Where a tap on the news of one subject leads; null when My ITMO has no single such subject. */
    suspend fun target(news: MarkNews, withBars: Boolean): MarkSubjectTarget?

    suspend fun markNotified(ids: Set<String>)

    /** Opening a subject reads it; the digest goes away with the last unread subject. */
    suspend fun markRead(half: StudyHalf, nameKey: String)

    /** Reads everything and removes the digest notification. */
    suspend fun markAllRead()

    /** Forgets one source's snapshot, keeps the unread subjects; the next check of it is a baseline. */
    suspend fun resetSource(source: MarkSource)
}
