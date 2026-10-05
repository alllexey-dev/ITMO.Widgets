package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.MutableAcademicTime
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.workResultOf
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.RecordingMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.markNews
import androidx.work.ListenableWorker.Result
import kotlin.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksCheckTest {

    private val tokens = FakeSessionTokenStore()
    private val settings = MarkSourcePreferences(InMemoryPreferencesDataStore())
    private val repository = FakeMarkTrackingRepository()
    private val notifier = RecordingMarksNotifier()
    private val chip = FakeBarsPreference(enabled = false)
    private val time = MutableAcademicTime(LocalDateTime.of(2026, 9, 7, 12, 0))
    private val check = MarksCheck(tokens, settings, repository, notifier, chip, time)

    @Test
    fun `no session or both sources off asks nothing and shows nothing`() = runTest {
        repository.news.value = listOf(markNews("Тестовый предмет 1"))
        tokens.signedIn = false
        assertEquals(CheckOutcome.SKIPPED, check.run())

        tokens.signedIn = true
        settings.setMyItmoMarksEnabled(false)
        settings.setBarsMarksEnabled(false)
        settings.setSheetMarksEnabled(false)
        assertEquals(CheckOutcome.SKIPPED, check.run())

        assertEquals(0, repository.myItmoChecks)
        assertEquals(0, repository.barsChecks)
        assertTrue(notifier.digests.isEmpty())
        assertTrue(repository.notified.isEmpty())
    }

    @Test
    fun `only My ITMO checks one source and one digest names both waiting subjects`() = runTest {
        repository.news.value = listOf(
            markNews("Тестовый предмет 1", detectedAt = Instant.parse("2026-09-07T08:00:00Z")),
            markNews("Тестовый предмет 2", detectedAt = Instant.parse("2026-09-07T09:00:00Z"))
        )

        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(1, repository.myItmoChecks)
        assertEquals(0, repository.barsChecks)
        val (digest, target) = notifier.digests.single()
        assertEquals(listOf("Тестовый предмет 2", "Тестовый предмет 1"), digest.subjects)
        assertNull(digest.single)
        assertNull(target)
        assertEquals(repository.news.value.map { it.id }.toSet(), repository.notified)
    }

    @Test
    fun `one waiting subject takes its target and the chip decides on the BARS journal`() = runTest {
        val news = markNews("Тестовый предмет 1")
        val target = MarkSubjectTarget(1, 3, "2026/2027", 11, null)
        repository.news.value = listOf(news)
        repository.targets[news.id] = target

        check.run()
        assertEquals(target, notifier.digests.single().second)
        assertEquals(listOf(news.id to false), repository.targetRequests)

        repository.news.value = listOf(news.copy(notified = false))
        chip.enabled = true
        check.run()
        assertEquals(news.id to true, repository.targetRequests.last())
    }

    @Test
    fun `an ended BARS session prompts once`() = runTest {
        settings.setBarsMarksEnabled(true)
        repository.barsResult = BarsCheck.SessionEnded

        assertEquals(CheckOutcome.DONE, check.run())
        assertEquals(1, repository.barsChecks)
        assertEquals(1, notifier.prompts)
        assertEquals(BarsLoginPrompt.SHOWN, settings.getBarsLoginPrompt())

        assertEquals(CheckOutcome.DONE, check.run())
        assertEquals(1, notifier.prompts)
        assertEquals(BarsLoginPrompt.SHOWN, settings.getBarsLoginPrompt())
    }

    @Test
    fun `the quiet hours keep the digest and the prompt for the first run after six`() = runTest {
        settings.setBarsMarksEnabled(true)
        repository.barsResult = BarsCheck.SessionEnded
        repository.news.value = listOf(markNews("Тестовый предмет 1"))
        time.current = LocalDateTime.of(2026, 9, 7, 3, 0)

        assertEquals(CheckOutcome.DONE, check.run())
        assertTrue(notifier.digests.isEmpty())
        assertEquals(0, notifier.prompts)
        assertTrue(repository.notified.isEmpty())
        assertEquals(BarsLoginPrompt.PENDING, settings.getBarsLoginPrompt())

        time.current = LocalDateTime.of(2026, 9, 7, 6, 5)
        repository.barsResult = BarsCheck.Done(MarkCheckResult.Compared(0))
        assertEquals(CheckOutcome.DONE, check.run())
        assertEquals(1, notifier.digests.size)
        assertEquals(1, notifier.prompts)
        assertEquals(BarsLoginPrompt.SHOWN, settings.getBarsLoginPrompt())
    }

    @Test
    fun `a network failure retries after delivery, an unauthorized answer and no BARS session do not`() = runTest {
        settings.setBarsMarksEnabled(true)
        repository.myItmoResult = AppResult.Failure(AppError.Network)
        repository.news.value = listOf(markNews("Тестовый предмет 1"))

        assertEquals(CheckOutcome.RETRY, check.run())
        assertEquals(1, notifier.digests.size)
        assertEquals(1, repository.barsChecks)

        repository.myItmoResult = AppResult.Failure(AppError.Unauthorized)
        assertEquals(CheckOutcome.DONE, check.run())

        repository.myItmoResult = AppResult.Success(MarkCheckResult.Compared(0))
        repository.barsResult = BarsCheck.NoSession
        assertEquals(CheckOutcome.DONE, check.run())
        assertEquals(0, notifier.prompts)
        assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
    }

    @Test
    fun `no network for BARS is retried and never prompts to sign in`() = runTest {
        settings.setBarsMarksEnabled(true)
        repository.barsResult = BarsCheck.Failed(AppError.Network)

        assertEquals(CheckOutcome.RETRY, check.run())

        assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
        assertEquals(0, notifier.prompts)
    }

    @Test
    fun `no network for both sources is retried twice, then the next period tries again`() = runTest {
        settings.setBarsMarksEnabled(true)
        repository.myItmoResult = AppResult.Failure(AppError.Network)
        repository.barsResult = BarsCheck.Failed(AppError.Network)

        val outcome = check.run()

        assertEquals(CheckOutcome.RETRY, outcome)
        assertEquals(Result.retry(), workResultOf(outcome, 0))
        assertEquals(Result.retry(), workResultOf(outcome, 1))
        assertEquals(Result.success(), workResultOf(outcome, 2))
        assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
    }


    @Test
    fun `all three sources off skip and only sheets check only sheets`() = runTest {
        settings.setMyItmoMarksEnabled(false)
        settings.setBarsMarksEnabled(false)
        settings.setSheetMarksEnabled(false)
        assertEquals(CheckOutcome.SKIPPED, check.run())
        assertEquals(0, repository.sheetsChecks)

        settings.setSheetMarksEnabled(true)
        assertEquals(CheckOutcome.DONE, check.run())

        assertEquals(1, repository.sheetsChecks)
        assertEquals(0, repository.myItmoChecks)
        assertEquals(0, repository.barsChecks)
    }

    @Test
    fun `no network for a sheet is retried after delivery`() = runTest {
        repository.news.value = listOf(markNews("Тестовый предмет 1"))
        repository.sheetsResult = SheetsCheck(MarkCheckResult.Compared(0), listOf(AppError.Network))

        assertEquals(CheckOutcome.RETRY, check.run())

        assertEquals(1, notifier.digests.size)
        assertEquals(1, repository.sheetsChecks)
    }

    @Test
    fun `a waiting record from a sheet is named in the digest`() = runTest {
        settings.setMyItmoMarksEnabled(false)
        repository.news.value = listOf(markNews("Тестовый предмет из таблицы"))

        check.run()

        assertEquals(listOf("Тестовый предмет из таблицы"), notifier.digests.single().first.subjects)
    }
}
