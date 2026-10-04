package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.testing.ClockAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runCurrent
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetChange
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.SheetsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkEventKind
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.MutableClock
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkRead
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkCheckResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarkTrackingRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @get:Rule val temporary = TemporaryFolder()
    private val folder by lazy { File(temporary.root, "marks") }
    private val store get() = MarksFileStore(folder, Gson())
    private val clock = MutableClock(Instant.parse("2026-09-07T09:00:00Z"))
    private val recordbook = FakeRecordbookRepository().apply {
        programs = AppResult.Success(listOf(program(1L, RecordbookPeriod("2026/2027", 3, 2, true), RecordbookPeriod("2025/2026", 2, 1, false))))
        subjects = AppResult.Success(listOf(subject(42, "Физика", 10.0)))
    }
    private val bars = FakeBarsMarkSource(BarsMarkRead.Journals(listOf(plan(1, "Химия", BarsCheckpointMark(10, 5.0, false))), 0))
    private val notifier = RecordingAppNotifier()
    private val sheets = FakeSheetScoresRepository()
    private var isu: Int? = 123
    private val users = object : CurrentUserProvider {
        override suspend fun getCurrentUser() = isu?.let { CurrentUser(it, null, null) }
    }

    @Test
    fun `the first My ITMO check is a baseline written for the account`() = runTest {
        val repository = repository()

        assertEquals(AppResult.Success(MarkCheckResult.Baseline), repository.checkMyItmo())

        assertEquals(emptyList<MarkNews>(), repository.observeNews().first())
        val stored = store.read()!!
        assertEquals(123, stored.owner)
        assertEquals("2026/2027-1", stored.myItmo?.half)
        assertEquals(clock.millis(), stored.myItmo?.fetchedAt)
    }

    @Test
    fun `the same answer finds nothing and new scores make an undelivered record`() = runTest {
        val repository = repository()
        repository.checkMyItmo()

        assertEquals(AppResult.Success(MarkCheckResult.Compared(0)), repository.checkMyItmo())
        clock.advance(Duration.ofHours(3))
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 12.5)))
        assertEquals(AppResult.Success(MarkCheckResult.Compared(1)), repository.checkMyItmo())

        val news = repository.observeNews().first().single()
        assertEquals("Физика", news.name)
        assertFalse(news.notified)
        assertEquals(clock.instant(), news.detectedAt)
    }

    @Test
    fun `only periods of the current half are asked and any failure writes nothing`() = runTest {
        recordbook.programs = AppResult.Success(listOf(
            program(1L, RecordbookPeriod("2026/2027", 3, 2, true), RecordbookPeriod("2025/2026", 2, 1, false)),
            program(2L, RecordbookPeriod("2026/2027", 1, 1, true), RecordbookPeriod("2026/2027", 2, 1, false))
        ))
        val repository = repository()
        repository.checkMyItmo()
        assertEquals(listOf(3, 1), recordbook.subjectRequests)
        val before = store.read()

        recordbook.programs = AppResult.Failure(AppError.Network)
        assertEquals(AppResult.Failure(AppError.Network), repository.checkMyItmo())
        recordbook.programs = AppResult.Success(listOf(program(1L, RecordbookPeriod("2026/2027", 3, 2, true))))
        recordbook.subjects = AppResult.Failure(AppError.Unknown())
        assertTrue(repository.checkMyItmo() is AppResult.Failure)

        assertEquals(before, store.read())
    }

    @Test
    fun `BARS checks compare journals and write nothing without an answer`() = runTest {
        val repository = repository()

        assertEquals(BarsCheck.Done(MarkCheckResult.Baseline), repository.checkBars())
        barsAnswer(BarsMarkRead.Journals(listOf(plan(1, "Химия", BarsCheckpointMark(10, 5.0, false), BarsCheckpointMark(11, 3.0, false))), 0))
        assertEquals(BarsCheck.Done(MarkCheckResult.Compared(1)), repository.checkBars())
        val before = store.read()

        barsAnswer(BarsMarkRead.NoSession)
        assertEquals(BarsCheck.NoSession, repository.checkBars())
        barsAnswer(BarsMarkRead.SessionEnded)
        assertEquals(BarsCheck.SessionEnded, repository.checkBars())
        barsAnswer(BarsMarkRead.Failure(AppError.Network))
        assertEquals(BarsCheck.Failed(AppError.Network), repository.checkBars())

        assertEquals(before, store.read())
        assertEquals(listOf(StudyHalf(2026, 1)), bars.requests.distinct())
    }

    @Test
    fun `no network in either source keeps both snapshots and the unread subjects`() = runTest {
        val repository = repository()
        repository.checkMyItmo()
        repository.checkBars()
        clock.advance(Duration.ofHours(3))
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 12.5)))
        repository.checkMyItmo()
        val before = store.read()
        val news = repository.observeNews().first()

        clock.advance(Duration.ofHours(3))
        recordbook.programs = AppResult.Failure(AppError.Network)
        assertEquals(AppResult.Failure(AppError.Network), repository.checkMyItmo())
        barsAnswer(BarsMarkRead.Failure(AppError.Network))
        assertEquals(BarsCheck.Failed(AppError.Network), repository.checkBars())

        assertEquals(before, store.read())
        assertEquals(news, repository.observeNews().first())
        assertEquals(1, news.size)
    }

    @Test
    fun `a subject changed in both sources is one record`() = runTest {
        barsAnswer(BarsMarkRead.Journals(listOf(plan(1, "Физика", BarsCheckpointMark(10, 5.0, false))), 0))
        val repository = repository()
        repository.checkMyItmo()
        repository.checkBars()

        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 12.0)))
        repository.checkMyItmo()
        barsAnswer(BarsMarkRead.Journals(listOf(plan(1, "ФИЗИКА", BarsCheckpointMark(10, 7.0, false))), 0))
        repository.checkBars()

        assertEquals(listOf("Физика"), repository.observeNews().first().map { it.name })
    }

    @Test
    fun `My ITMO repeating what BARS already had is not news`() = runTest {
        barsAnswer(BarsMarkRead.Journals(listOf(plan(1, "Физика", score = 17.5, rate = "4/B")), 0))
        val repository = repository()
        repository.checkMyItmo()
        repository.checkBars()

        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 17.5, "4/B")))

        assertEquals(AppResult.Success(MarkCheckResult.Compared(0)), repository.checkMyItmo())
        assertEquals(emptyList<MarkNews>(), repository.observeNews().first())
    }

    @Test
    fun `a new half-year starts both sources over and keeps what is unread`() = runTest {
        clock.now = Instant.parse("2027-01-25T09:00:00Z")
        recordbook.programs = AppResult.Success(listOf(program(1L, RecordbookPeriod("2026/2027", 3, 2, false), RecordbookPeriod("2026/2027", 4, 2, true))))
        val repository = repository()
        repository.checkMyItmo()
        repository.checkBars()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 12.5)))
        repository.checkMyItmo()

        clock.now = Instant.parse("2027-02-01T09:00:00Z")

        assertEquals(AppResult.Success(MarkCheckResult.Baseline), repository.checkMyItmo())
        assertEquals(BarsCheck.Done(MarkCheckResult.Baseline), repository.checkBars())
        val stored = store.read()!!
        assertEquals("2026/2027-2", stored.myItmo?.half)
        assertEquals("2026/2027-2", stored.bars?.half)
        assertEquals(listOf(4), recordbook.subjectRequests.takeLast(1))
        assertEquals(listOf("Физика"), repository.observeNews().first().map { it.name })
    }

    @Test
    fun `a list advances the snapshot only when it was asked after the last write`() = runTest {
        val empty = repository()
        empty.recordMyItmoSeen(empty.readStarted(), HALF, 1L, 3, listOf(subject(42, "Физика", 20.0)))
        assertNull(store.read())

        val repository = repository()
        val early = repository.readStarted()
        clock.advance(Duration.ofSeconds(1))
        repository.checkMyItmo()
        repository.recordMyItmoSeen(early, HALF, 1L, 3, listOf(subject(42, "Физика", 20.0)))
        assertEquals(emptyList<MarkNews>(), repository.observeNews().first())

        clock.advance(Duration.ofSeconds(1))
        repository.recordMyItmoSeen(repository.readStarted(), HALF, 1L, 3, listOf(subject(42, "Физика", 20.0)))

        val news = repository.observeNews().first().single()
        assertEquals("Физика", news.name)
        assertTrue(news.notified)
        assertEquals(20.0, store.read()!!.myItmo!!.subjects.single().score!!, 0.0)
    }

    @Test
    fun `a BARS list advances only its plans and carries the others`() = runTest {
        barsAnswer(BarsMarkRead.Journals(listOf(plan(1, "Химия"), plan(2, "Физика", BarsCheckpointMark(20, 4.0, false))), 0))
        val repository = repository()
        repository.checkBars()
        clock.advance(Duration.ofSeconds(1))

        repository.recordBarsSeen(repository.readStarted(), HALF, listOf(plan(1, "Химия", BarsCheckpointMark(10, 5.0, false))))

        assertEquals(listOf("Химия"), repository.observeNews().first().map { it.name })
        assertEquals(setOf(1L, 2L), store.read()!!.bars!!.plans.map { it.planId }.toSet())
    }

    @Test
    fun `a background answer asked before a list wrote is stale`() = runTest {
        val repository = repository()
        repository.checkMyItmo()
        val asked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        recordbook.subjectLoader = { asked.complete(Unit); release.await(); AppResult.Success(listOf(subject(42, "Физика", 11.0))) }

        val check = async { repository.checkMyItmo() }
        asked.await()
        clock.advance(Duration.ofSeconds(1))
        repository.recordMyItmoSeen(repository.readStarted(), HALF, 1L, 3, listOf(subject(42, "Физика", 12.0)))
        release.complete(Unit)

        assertEquals(AppResult.Success(MarkCheckResult.Stale), check.await())
        assertEquals(12.0, store.read()!!.myItmo!!.subjects.single().score!!, 0.0)
    }

    @Test
    fun `reading the last subject removes the digest and reading all always does`() = runTest {
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 10.0), subject(43, "Химия", 10.0)))
        val repository = repository()
        repository.checkMyItmo()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 11.0), subject(43, "Химия", 11.0)))
        repository.checkMyItmo()

        repository.markRead(HALF, "физика")
        assertEquals(emptyList<Pair<String, Int>>(), notifier.cancelled)
        repository.markRead(HALF, "химия")
        assertEquals(listOf(AppNotificationChannels.MARKS to 1), notifier.cancelled)

        repository.markAllRead()
        assertEquals(emptyList<MarkNews>(), repository.observeNews().first())
        assertEquals(2, notifier.cancelled.size)
    }

    @Test
    fun `resetting BARS forgets its snapshot only and drops a BARS answer already on its way`() = runTest {
        val repository = repository()
        repository.checkMyItmo()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 11.0)))
        repository.checkMyItmo()
        repository.checkBars()

        repository.resetSource(MarkSource.BARS)
        val stored = store.read()!!
        assertNull(stored.bars)
        assertTrue(stored.myItmo != null)
        assertEquals(1, stored.news.size)

        val asked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        bars.beforeAnswer = { asked.complete(Unit); release.await() }
        val check = async { repository.checkBars() }
        asked.await()
        repository.resetSource(MarkSource.BARS)
        release.complete(Unit)

        assertEquals(BarsCheck.Done(MarkCheckResult.Stale), check.await())
        assertNull(store.read()!!.bars)
    }

    @Test
    fun `clearing the session deletes the file and a check already asking writes nothing`() = runTest {
        val repository = repository()
        repository.checkMyItmo()
        val asked = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        recordbook.subjectLoader = { asked.complete(Unit); release.await(); AppResult.Success(listOf(subject(42, "Физика", 11.0))) }

        val check = async { repository.checkMyItmo() }
        asked.await()
        repository.clearSessionData()
        release.complete(Unit)

        assertEquals(AppResult.Failure(AppError.Unauthorized), check.await())
        assertFalse(File(folder, "state.json").exists())
        assertEquals(emptyList<MarkNews>(), repository.observeNews().first())
    }

    @Test
    fun `another account's file or a corrupt file starts over with a baseline`() = runTest {
        repository().checkMyItmo()
        isu = 456

        assertEquals(AppResult.Success(MarkCheckResult.Baseline), repository().checkMyItmo())
        assertEquals(456, store.read()!!.owner)

        File(folder, "state.json").writeText("not json")
        assertEquals(AppResult.Success(MarkCheckResult.Baseline), repository().checkMyItmo())
    }

    @Test
    fun `a new repository reads the same state and old records go on the next write`() = runTest {
        val first = repository()
        first.checkMyItmo()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 11.0)))
        first.checkMyItmo()
        assertEquals(listOf("Физика"), repository().observeNews().first().map { it.name })

        clock.advance(Duration.ofDays(31))
        val later = repository()
        assertEquals(emptyList<MarkNews>(), later.observeNews().first())
        later.markNotified(setOf("unknown"))
        assertEquals(emptyList<StoredMarkNews>(), store.read()!!.news)
    }

    @Test
    fun `a tap target comes from the snapshots`() = runTest {
        barsAnswer(BarsMarkRead.Journals(listOf(plan(8, "Физика")), 0))
        val repository = repository()
        repository.checkMyItmo()
        repository.checkBars()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 11.0)))
        repository.checkMyItmo()
        val news = repository.observeNews().first().single()

        assertEquals(MarkSubjectTarget(1L, 3, "2026/2027", 42L, bars = null), repository.target(news, withBars = false))
        assertEquals(BarsJournalReference(8L, "flow", "7", 2026, 1), repository.target(news, withBars = true)?.bars)
    }


    @Test
    fun `a changed sheet total is one undelivered record of the half-year`() = runTest {
        sheets.checkResult = SheetCheck(listOf(SheetChange(SHEET_SCOPE, MarkEventKind.MARK_CHANGED)), emptyList())
        val repository = repository()

        val check = repository.checkSheets()

        assertEquals(SheetsCheck(MarkCheckResult.Compared(1), emptyList()), check)
        assertEquals(listOf(HALF), sheets.checks)
        val news = repository.observeNews().first().single()
        assertEquals("Тестовый предмет", news.name)
        assertEquals(HALF, news.half)
        assertFalse(news.notified)
        assertEquals(listOf(news.id), store.read()!!.news.map { it.id })
    }

    @Test
    fun `a sheet and My ITMO change of one subject are one record named by My ITMO`() = runTest {
        val repository = repository()
        repository.checkMyItmo()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 12.0)))
        repository.checkMyItmo()
        sheets.checkResult = SheetCheck(
            listOf(SheetChange(ResourceScope(142, "ФИЗИКА", "2026-1"), MarkEventKind.MARK_ADDED)), emptyList()
        )

        repository.checkSheets()

        assertEquals(listOf("Физика"), repository.observeNews().first().map { it.name })
    }

    @Test
    fun `resetting sheets untracks them, keeps the snapshots and drops a check already on its way`() = runTest {
        barsAnswer(BarsMarkRead.Journals(listOf(plan(1, "Химия", BarsCheckpointMark(10, 5.0, false))), 0))
        val repository = repository()
        repository.checkMyItmo()
        repository.checkBars()
        recordbook.subjects = AppResult.Success(listOf(subject(42, "Физика", 11.0)))
        repository.checkMyItmo()
        val before = store.read()!!

        repository.resetSource(MarkSource.SHEETS)

        assertEquals(1, sheets.untrackCalls)
        assertEquals(before, store.read())
        sheets.checkResult = SheetCheck(listOf(SheetChange(SHEET_SCOPE, MarkEventKind.MARK_CHANGED)), emptyList())
        val gate = CompletableDeferred<Unit>()
        sheets.checkGate = gate
        val check = async { repository.checkSheets() }
        runCurrent()
        repository.resetSource(MarkSource.SHEETS)
        gate.complete(Unit)

        assertEquals(MarkCheckResult.Stale, check.await().result)
        assertEquals(listOf("Физика"), store.read()!!.news.map { it.name })
    }

    @Test
    fun `clearing the session during a sheet check writes nothing`() = runTest {
        val repository = repository()
        sheets.checkResult = SheetCheck(listOf(SheetChange(SHEET_SCOPE, MarkEventKind.MARK_CHANGED)), emptyList())
        val gate = CompletableDeferred<Unit>()
        sheets.checkGate = gate
        val check = async { repository.checkSheets() }
        runCurrent()

        repository.clearSessionData()
        gate.complete(Unit)

        assertEquals(SheetsCheck(MarkCheckResult.Stale, listOf(AppError.Unauthorized)), check.await())
        assertFalse(File(folder, "state.json").exists())
        assertEquals(emptyList<MarkNews>(), repository.observeNews().first())
    }

    @Test
    fun `failed sheet downloads reach the check errors`() = runTest {
        sheets.checkResult = SheetCheck(emptyList(), listOf(AppError.Network))

        assertEquals(SheetsCheck(MarkCheckResult.Compared(0), listOf(AppError.Network)), repository().checkSheets())
    }
    @Test
    fun `the demo has two subjects with news until they are read`() = runTest {
        val repository = repository(FakeDemoMode(active = true))

        val news = repository.observeNews().first()
        assertEquals(listOf(DemoStudy.DATABASES.name, DemoStudy.DISCRETE.name), news.map { it.name })
        assertEquals(DemoStudy.DATABASES.id * 10 + 3, repository.target(news.first(), withBars = false)?.entryId)

        repository.markAllRead()

        assertTrue(repository.observeNews().first().isEmpty())
        assertFalse(folder.exists())
    }

    private fun repository(demo: DemoMode = noDemo()) =
        MarkTrackingRepositoryImpl(recordbook, bars, store, ClockAcademicTime(clock), clock, notifier, users, sheets, demo, dispatchers)

    private fun barsAnswer(answer: BarsMarkRead) {
        bars.answers.clear()
        bars.answers += answer
    }

    private companion object {
        val HALF = StudyHalf(2026, 1)
        val SHEET_SCOPE = ResourceScope(1, "Тестовый предмет", "2026-1")

        fun program(id: Long, vararg periods: RecordbookPeriod) = RecordbookProgram(id, "Тестовая программа $id", periods.toList())

        fun subject(id: Long, name: String, score: Double?, rate: String? = null): RecordbookSubject =
            recordbookSubject(id, name).copy(score = score, rate = rate, disciplineId = id + 100)

        fun plan(id: Long, name: String, vararg marks: BarsCheckpointMark, score: Double? = null, rate: String? = null) =
            BarsPlanMarks(id, "flow", "7", name, score, rate, null, false, marks.toList())
    }
}
