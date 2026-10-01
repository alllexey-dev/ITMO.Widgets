package dev.alllexey.itmowidgets.feature.recordbook

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkRead
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigest
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MyItmoSubjectMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.ReadStamp
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Unread subjects from [news]; reads, resets and advances are recorded, not compared. */
class FakeMarkTrackingRepository(vararg initial: MarkNews) : MarkTrackingRepository {
    val news = MutableStateFlow(initial.toList())
    var myItmoResult: AppResult<MarkCheckResult> = AppResult.Success(MarkCheckResult.Compared(0))
    var barsResult: BarsCheck = BarsCheck.Done(MarkCheckResult.Compared(0))
    var sheetsResult = SheetsCheck(MarkCheckResult.Compared(0), emptyList())
    var sheetsChecks = 0
    var myItmoChecks = 0
    var barsChecks = 0
    var stamp = 0L
    val seenMyItmo = mutableListOf<SeenMyItmo>()
    val seenBars = mutableListOf<Pair<StudyHalf, List<BarsPlanMarks>>>()
    val read = mutableListOf<Pair<StudyHalf, String>>()
    val notified = mutableSetOf<String>()
    val resets = mutableListOf<MarkSource>()
    var markAllReadCalls = 0
    val targets = mutableMapOf<String, MarkSubjectTarget>()
    val targetRequests = mutableListOf<Pair<String, Boolean>>()

    data class SeenMyItmo(val half: StudyHalf, val programId: Long, val semester: Int, val subjects: List<RecordbookSubject>)

    override fun observeNews(): Flow<List<MarkNews>> = news

    override suspend fun checkMyItmo(): AppResult<MarkCheckResult> {
        myItmoChecks++
        return myItmoResult
    }

    override suspend fun checkBars(): BarsCheck {
        barsChecks++
        return barsResult
    }

    override suspend fun checkSheets(): SheetsCheck {
        sheetsChecks++
        return sheetsResult
    }

    override fun readStarted(): ReadStamp = ReadStamp(stamp)

    override suspend fun recordMyItmoSeen(
        stamp: ReadStamp,
        half: StudyHalf,
        programId: Long,
        semester: Int,
        subjects: List<RecordbookSubject>
    ) {
        seenMyItmo += SeenMyItmo(half, programId, semester, subjects)
    }

    override suspend fun recordBarsSeen(stamp: ReadStamp, half: StudyHalf, plans: List<BarsPlanMarks>) {
        seenBars += half to plans
    }

    override suspend fun target(news: MarkNews, withBars: Boolean): MarkSubjectTarget? {
        targetRequests += news.id to withBars
        return targets[news.id]
    }

    override suspend fun markNotified(ids: Set<String>) {
        notified += ids
        news.value = news.value.map { if (it.id in ids) it.copy(notified = true) else it }
    }

    override suspend fun markRead(half: StudyHalf, nameKey: String) {
        read += half to nameKey
        news.value = news.value.filterNot { it.half == half && it.nameKey == nameKey }
    }

    override suspend fun markAllRead() {
        markAllReadCalls++
        news.value = emptyList()
    }

    override suspend fun resetSource(source: MarkSource) {
        resets += source
    }
}

/** Answers [read] from [answers] in order; the last answer repeats. */
class FakeBarsMarkSource(vararg answers: BarsMarkRead) : BarsMarkSource {
    val answers = ArrayDeque(answers.toList())
    val requests = mutableListOf<StudyHalf>()
    /** Runs inside [read] before the answer, for tests that hold a read open. */
    var beforeAnswer: (suspend () -> Unit)? = null

    override suspend fun read(half: StudyHalf): BarsMarkRead {
        requests += half
        beforeAnswer?.invoke()
        return if (answers.size > 1) answers.removeFirst() else answers.firstOrNull() ?: BarsMarkRead.NoSession
    }
}

/** Counts calls; no work is scheduled. */
class FakeMarksScheduler : MarksScheduler {
    var ensureCalls = 0
    var runOnceCalls = 0
    var cancelCalls = 0

    override fun ensurePeriodic() {
        ensureCalls++
    }

    override fun runOnce() {
        runOnceCalls++
    }

    override fun cancel() {
        cancelCalls++
    }
}

/** Records every digest with its target and every sign-in prompt. */
class RecordingMarksNotifier : MarksNotifier {
    val digests = mutableListOf<Pair<MarkDigest, MarkSubjectTarget?>>()
    var prompts = 0

    override fun showDigest(digest: MarkDigest, target: MarkSubjectTarget?) {
        digests += digest to target
    }

    override fun showBarsPrompt() {
        prompts++
    }
}

/** Counts successful BARS answers reported by the client. */
class CountingBarsSessionListener : BarsSessionListener {
    var answers = 0

    override suspend fun onBarsAnswered() {
        answers++
    }
}

val TEST_HALF = StudyHalf(2026, 1)

fun myItmoMark(entryId: Long, name: String = "Тестовый предмет", score: Double? = null, rate: String? = null) =
    MyItmoSubjectMark(1L, 3, entryId, entryId + 100, name, score, rate)

fun barsPlan(planId: Long, name: String = "Тестовый предмет $planId", vararg marks: BarsCheckpointMark) =
    BarsPlanMarks(planId, "flow", "7", name, null, null, null, false, marks.toList())

fun markNews(
    name: String = "Тестовый предмет",
    half: StudyHalf = TEST_HALF,
    detectedAt: Instant = Instant.parse("2026-09-07T09:00:00Z"),
    notified: Boolean = false
): MarkNews {
    val key = subjectNameKey(name)
    return MarkNews(MarkNews.idOf(half, key), half, key, name, detectedAt, notified)
}
