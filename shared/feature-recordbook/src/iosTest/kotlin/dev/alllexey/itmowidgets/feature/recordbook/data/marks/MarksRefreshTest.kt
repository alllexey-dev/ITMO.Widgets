package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigest
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.ReadStamp
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

/**
 * The marks step of the iOS runner: KM-11b2's check with the BARS renewal's two endings (an ended session asks for
 * the sign-in once, a failure is retried silently), and night marks handed to the system for 06:00.
 */
class MarksRefreshTest {

    private val tokens = FakeSessionTokenStore()
    private val markSources = MarkSourcePreferences(InMemoryPreferencesDataStore())
    private val repository = FakeMarks()
    private val notifier = RecordingMorningNotifier()

    @Test
    fun anEndedBarsSessionAsksForTheSignInOnce() = runTest {
        barsOnly()
        repository.barsResult = BarsCheck.SessionEnded

        val first = refresh(at = DAY).run()
        val second = refresh(at = DAY).run()

        assertEquals(CheckOutcome.DONE, first)
        assertEquals(CheckOutcome.DONE, second)
        assertEquals(1, notifier.prompts)
        assertEquals(BarsLoginPrompt.SHOWN, markSources.getBarsLoginPrompt())
        assertTrue(notifier.scheduledPrompts.isEmpty())
    }

    @Test
    fun aBarsFailureIsRetriedWithoutTheSignInReminder() = runTest {
        barsOnly()
        repository.barsResult = BarsCheck.Failed(AppError.Network)

        val outcome = refresh(at = DAY).run()

        assertEquals(CheckOutcome.RETRY, outcome)
        assertEquals(0, notifier.prompts)
        assertEquals(BarsLoginPrompt.NONE, markSources.getBarsLoginPrompt())
    }

    @Test
    fun withoutBarsCookiesTheSessionEndsAtNightAndTheReminderWaitsForSix() = runTest {
        barsOnly()
        repository.barsResult = BarsCheck.SessionEnded

        assertEquals(CheckOutcome.DONE, refresh(at = NIGHT).run())

        assertEquals(0, notifier.prompts)
        assertEquals(listOf(SIX_MOSCOW), notifier.scheduledPrompts)
        assertEquals(BarsLoginPrompt.SHOWN, markSources.getBarsLoginPrompt())
    }

    @Test
    fun nightMarksAreScheduledForSixMoscowTimeAndCountAsDelivered() = runTest {
        repository.news.value = listOf(news("Базы данных"))
        val target = MarkSubjectTarget(1, 3, "2026/2027", 11, bars = null)
        repository.targets[news("Базы данных").id] = target

        assertEquals(CheckOutcome.DONE, refresh(at = NIGHT).run())

        assertTrue(notifier.digests.isEmpty())
        val (digest, scheduledTarget, at) = notifier.scheduledDigests.single()
        assertEquals(listOf("Базы данных"), digest.subjects)
        assertEquals(target, scheduledTarget)
        assertEquals(SIX_MOSCOW, at)
        assertEquals(setOf(news("Базы данных").id), repository.notified)
    }

    @Test
    fun dayMarksAreShownAtOnceByTheCheckItself() = runTest {
        repository.news.value = listOf(news("Базы данных"), news("Физика"))

        assertEquals(CheckOutcome.DONE, refresh(at = DAY).run())

        assertEquals(listOf("Базы данных", "Физика"), notifier.digests.single().subjects.sorted())
        assertTrue(notifier.scheduledDigests.isEmpty())
    }

    @Test
    fun signedOutOrEverySourceOffSchedulesNothingAtNight() = runTest {
        repository.news.value = listOf(news("Базы данных"))
        tokens.signedIn = false
        assertEquals(CheckOutcome.SKIPPED, refresh(at = NIGHT).run())

        tokens.signedIn = true
        markSources.setMyItmoMarksEnabled(false)
        markSources.setSheetMarksEnabled(false)
        assertEquals(CheckOutcome.SKIPPED, refresh(at = NIGHT).run())

        assertTrue(notifier.scheduledDigests.isEmpty())
        assertEquals(0, repository.checks)
    }

    private suspend fun barsOnly() {
        markSources.setMyItmoMarksEnabled(false)
        markSources.setSheetMarksEnabled(false)
        markSources.setBarsMarksEnabled(true)
    }

    private fun refresh(at: LocalDateTime): MarksRefresh {
        val time = FixedAcademicTime(at)
        val check = MarksCheck(tokens, markSources, repository, notifier, NoBarsOverlay, time)
        return MarksRefresh(check, repository, markSources, NoBarsOverlay, notifier, time)
    }

    private fun news(name: String): MarkNews {
        val key = subjectNameKey(name)
        return MarkNews(MarkNews.idOf(HALF, key), HALF, key, name, Instant.parse("2026-10-06T21:00:00Z"), false)
    }

    private class RecordingMorningNotifier : MorningMarksNotifier {
        val digests = mutableListOf<MarkDigest>()
        var prompts = 0
        val scheduledDigests = mutableListOf<Triple<MarkDigest, MarkSubjectTarget?, Instant>>()
        val scheduledPrompts = mutableListOf<Instant>()

        override fun showDigest(digest: MarkDigest, target: MarkSubjectTarget?) {
            digests += digest
        }

        override fun showBarsPrompt() {
            prompts++
        }

        override suspend fun showDigestAt(digest: MarkDigest, target: MarkSubjectTarget?, at: Instant) {
            scheduledDigests += Triple(digest, target, at)
        }

        override suspend fun showBarsPromptAt(at: Instant) {
            scheduledPrompts += at
        }
    }

    /** Unread subjects from [news] and the answers of the three sources; nothing is compared. */
    private class FakeMarks : MarkTrackingRepository {
        val news = MutableStateFlow<List<MarkNews>>(emptyList())
        var barsResult: BarsCheck = BarsCheck.Done(MarkCheckResult.Compared(0))
        val targets = mutableMapOf<String, MarkSubjectTarget>()
        val notified = mutableSetOf<String>()
        var checks = 0

        override fun observeNews(): Flow<List<MarkNews>> = news

        override suspend fun checkMyItmo(): AppResult<MarkCheckResult> {
            checks++
            return AppResult.Success(MarkCheckResult.Compared(0))
        }

        override suspend fun checkBars(): BarsCheck {
            checks++
            return barsResult
        }

        override suspend fun checkSheets(): SheetsCheck {
            checks++
            return SheetsCheck(MarkCheckResult.Compared(0), emptyList())
        }

        override fun readStarted(): ReadStamp = ReadStamp(0)

        override suspend fun recordMyItmoSeen(
            stamp: ReadStamp,
            half: StudyHalf,
            programId: Long,
            semester: Int,
            subjects: List<RecordbookSubject>,
        ) = Unit

        override suspend fun recordBarsSeen(stamp: ReadStamp, half: StudyHalf, plans: List<BarsPlanMarks>) = Unit

        override suspend fun target(news: MarkNews, withBars: Boolean): MarkSubjectTarget? = targets[news.id]

        override suspend fun markNotified(ids: Set<String>) {
            notified += ids
            news.value = news.value.map { if (it.id in ids) it.copy(notified = true) else it }
        }

        override suspend fun markRead(half: StudyHalf, nameKey: String) = Unit

        override suspend fun markAllRead() {
            news.value = emptyList()
        }

        override suspend fun resetSource(source: MarkSource) = Unit
    }

    private object NoBarsOverlay : BarsPreferenceRepository {
        override suspend fun isEnabled(): Boolean = false

        override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> = AppResult.Success(Unit)
    }

    private companion object {
        val HALF = StudyHalf(2026, 1)
        val DAY = LocalDateTime(2026, 10, 7, 12, 0)
        val NIGHT = LocalDateTime(2026, 10, 7, 3, 0)
        val SIX_MOSCOW: Instant = Instant.parse("2026-10-07T03:00:00Z")
    }
}
