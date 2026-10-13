package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.TeacherReviewArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.resources.title
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLesson
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsPreference
import dev.alllexey.itmowidgets.feature.recordbook.FakeBarsRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.barsJournal
import dev.alllexey.itmowidgets.feature.recordbook.barsSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.recordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookSubjectViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val repository = FakeRecordbookRepository()
    private val bars = FakeBarsRepository()
    private val preference = FakeBarsPreference()
    private val resources = FakeSubjectLinksRepository()
    private val levels = FakeTeacherLevelsRepository()
    private val marks = FakeMarkTrackingRepository()
    private val lessons = FakeSubjectLessonsGateway()
    private val scheduleRefresh = FakeScheduleRefreshGateway()
    private val bindingStore = FakeSubjectBindingStore()
    private val gate = FakeBackendGate(optedIn = true)

    /** A past period (spring 2025/2026): no lessons ahead. */
    private fun model(withBars: Boolean = false, sheets: FakeSheetScoresRepository = FakeSheetScoresRepository()) =
        subjectModel(args(semester = 2, year = "2025/2026", withBars = withBars), sheets)

    /** The current period: FixedAcademicTime is 2026-09-07, autumn of 2026/2027, semester 3. */
    private fun currentPeriodModel(
        subject: RecordbookSubject = recordbookSubject(),
        sheets: FakeSheetScoresRepository = FakeSheetScoresRepository(),
    ): RecordbookSubjectViewModel {
        repository.subjects = AppResult.Success(listOf(subject))
        return subjectModel(args(semester = 3, year = "2026/2027"), sheets)
    }

    private fun args(semester: Int, year: String, withBars: Boolean = false) = RecordbookSubjectArgs(
        entryId = 42, programId = 1, semester = semester, studyYear = year,
        barsPlan = if (withBars) 8L else null, barsType = if (withBars) "flow" else null, barsIdentifier = if (withBars) "7" else null,
    )

    private fun subjectModel(args: RecordbookSubjectArgs, sheets: FakeSheetScoresRepository): RecordbookSubjectViewModel {
        val time = FixedAcademicTime(LocalDate(2026, 9, 7))
        return RecordbookSubjectViewModel(args, repository, bars, preference, RecordbookSportResolver(FakeSportScoreRepository()), time,
            marks, SubjectLessonsLoader(lessons, scheduleRefresh, bindingStore, SubjectContextResolver(), time),
            SubjectLinksLoader(resources), SubjectSheetLoader(sheets, time), SubjectTeacherLevelsLoader(levels), gate)
    }

    private val RecordbookSubjectViewModel.content get() = uiState.value as RecordbookSubjectUiState.Content
    private val RecordbookSubjectViewModel.scores get() = content.scores
    private val RecordbookSubjectViewModel.lessonsTab get() = content.lessons
    private val RecordbookSubjectViewModel.materials get() = content.materials
    private val RecordbookSubjectViewModel.links get() = materials as SubjectMaterials.Content

    @Test fun `opening the page reads the subject's new marks once`() = runTest {
        val vm = model(); advanceUntilIdle()
        assertEquals(listOf(StudyHalf(2025, 2) to subjectNameKey("Тестовый предмет")), marks.read)

        vm.refresh(RefreshMode.Pull); advanceUntilIdle()
        assertEquals(1, marks.read.size)
    }

    @Test fun `the header names the subject, its kind and semester, and the tabs start on scores`() = runTest {
        val vm = model(); advanceUntilIdle()
        assertEquals("Тестовый предмет", vm.content.title)
        assertEquals(UiText.Res(RecordbookStrings.subject_subtitle, listOf("Экзамен", 2)), vm.content.subtitle)
        assertEquals(SubjectTab.SCORES, vm.content.tab)
        vm.selectTab(SubjectTab.MATERIALS)
        assertEquals(SubjectTab.MATERIALS, vm.content.tab)
    }

    // --- Баллы

    @Test fun `a BARS journal lays its values over My ITMO and supplies the checkpoints`() = runTest {
        val control = RecordbookControl(6, "Работа", 7.5, 6.0, 10.0, true, null, null)
        preference.enabled = true
        bars.details = AppResult.Success(BarsSubjectDetails(barsSubject(rate = null), listOf(control)))
        val vm = model(withBars = true); advanceUntilIdle()
        assertEquals(listOf(barsJournal()), bars.journalRequests)
        assertEquals("91,5", vm.scores.hero.points)
        assertEquals(UiText.Res(RecordbookStrings.recordbook_source_bars), vm.scores.hero.source)
        val row = vm.scores.groups.single().rows.single()
        assertEquals(UiText.Res(RecordbookStrings.recordbook_control_score, listOf(UiText.Dynamic("7,5"), "10")), row.score)
        assertEquals(UiText.Res(RecordbookStrings.recordbook_control_minimum, listOf("6")), row.minimum)
        assertEquals(RecordbookTone.OK, row.tone)
        assertEquals(0, repository.controlRequests)
        assertNull(vm.scores.barsNotice)
    }

    @Test fun `a failed BARS journal falls back to My ITMO with a notice, an ended session asks to sign in`() = runTest {
        preference.enabled = true
        bars.details = AppResult.Failure(AppError.Network)
        val failed = model(withBars = true); advanceUntilIdle()
        assertEquals("75", failed.scores.hero.points)
        assertEquals(RecordbookBarsNotice.Failed(AppError.Network), failed.scores.barsNotice)
        assertEquals(1, repository.controlRequests)

        bars.details = AppResult.Failure(AppError.Unauthorized)
        val expired = model(withBars = true); advanceUntilIdle()
        assertEquals(RecordbookBarsNotice.Expired, expired.scores.barsNotice)
    }

    @Test fun `without a journal in the arguments a connected BARS finds the subject in its period`() = runTest {
        preference.enabled = true
        bars.subjects = AppResult.Success(listOf(barsSubject(rate = null)))
        bars.details = AppResult.Success(BarsSubjectDetails(barsSubject(rate = null), emptyList()))
        val vm = model(); advanceUntilIdle()
        assertEquals(listOf(barsJournal()), bars.journalRequests)
        assertEquals("91,5", vm.scores.hero.points)

        bars.subjects = AppResult.Success(emptyList())
        val absent = model(); advanceUntilIdle()
        assertNull(absent.scores.barsNotice)
        assertEquals("75", absent.scores.hero.points)
    }

    @Test fun `without BARS and checkpoints the page offers BARS quietly, with BARS it says there are none`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject(details = false)))
        val vm = model(); advanceUntilIdle()
        assertTrue(vm.scores.barsOffer)
        assertFalse(vm.scores.noDetails)
        assertEquals(0, repository.controlRequests)

        vm.barsSignedIn(); advanceUntilIdle()
        assertTrue(preference.enabled)
        assertFalse(vm.scores.barsOffer)
        assertTrue(vm.scores.noDetails)
    }

    @Test fun `a fresh page shows the cached subject and checkpoints before the network answers`() = runTest {
        val control = RecordbookControl(6, "Работа", 7.5, 0.0, 10.0, true, null, null)
        repository.cachedSubjects = listOf(recordbookSubject(name = "Из кэша"))
        repository.cachedControls = listOf(control)
        val wait = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        repository.subjectLoader = { wait.await() }
        val vm = model(); runCurrent()
        assertFalse(vm.content.refreshing)
        assertEquals("Из кэша", vm.content.title)
        assertEquals(1, vm.scores.groups.single().rows.size)
        wait.complete(repository.subjects); advanceUntilIdle()
        assertEquals("Тестовый предмет", vm.content.title)
    }

    @Test fun `a cached subject without cached checkpoints still starts from the placeholder`() = runTest {
        repository.cachedSubjects = listOf(recordbookSubject(name = "Из кэша"))
        val wait = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        repository.subjectLoader = { wait.await() }
        val vm = model(); runCurrent()
        assertEquals(RecordbookSubjectUiState.Loading, vm.uiState.value)
        wait.complete(repository.subjects); advanceUntilIdle()
        assertTrue(vm.uiState.value is RecordbookSubjectUiState.Content)
    }

    @Test fun `a subject without a BARS journal never asks BARS while BARS is off`() = runTest {
        model(); advanceUntilIdle()
        assertTrue(bars.journalRequests.isEmpty())
        assertTrue(bars.periodRequests.isEmpty())
    }

    @Test fun `failed checkpoints leave the hero available`() = runTest {
        repository.controls = AppResult.Failure(AppError.Forbidden)
        val vm = model(); advanceUntilIdle()
        assertEquals("75", vm.scores.hero.points)
        assertEquals(AppError.Forbidden, vm.scores.controlsError)
    }

    @Test fun `an unknown entry fails without asking for checkpoints, a retry shows its progress`() = runTest {
        repository.subjects = AppResult.Success(emptyList())
        val vm = model(); advanceUntilIdle()
        assertEquals(RecordbookSubjectUiState.Failed(AppError.NotFound, retrying = false), vm.uiState.value)
        assertEquals(0, repository.controlRequests)

        val wait = CompletableDeferred<AppResult<List<RecordbookSubject>>>()
        repository.subjectLoader = { wait.await() }
        vm.refresh(RefreshMode.Force); runCurrent()
        assertEquals(RecordbookSubjectUiState.Failed(AppError.NotFound, retrying = true), vm.uiState.value)
    }

    @Test fun `a failed pull keeps the page and stops the indicator`() = runTest {
        val vm = model(); advanceUntilIdle()
        repository.subjects = AppResult.Failure(AppError.Network)
        vm.refresh(RefreshMode.Pull); vm.refresh(RefreshMode.Pull); advanceUntilIdle()
        assertEquals("Тестовый предмет", vm.content.title)
        assertEquals(AppError.Network, vm.content.refreshError)
        assertFalse(vm.content.refreshing)
    }

    @Test fun `the hero shows the grade by points and the next step, a final result as its pill`() = runTest {
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = 63.5)))
        val exam = model(); advanceUntilIdle()
        assertEquals(UiText.Dynamic("3E"), exam.scores.hero.grade)
        assertEquals(UiText.Res(RecordbookStrings.subject_grade_next, listOf("3D", "4,5")), exam.scores.hero.next)
        assertEquals(listOf("E", "D", "C", "B", "A"), exam.scores.hero.ticks.map { (it.label as UiText.Dynamic).value })

        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(controlType = "Зачёт", rate = null, score = 52.0)))
        val credit = model(); advanceUntilIdle()
        assertNull(credit.scores.hero.grade)
        assertEquals(UiText.Res(RecordbookStrings.subject_credit_next, listOf("8")), credit.scores.hero.next)
        assertEquals(1, credit.scores.hero.ticks.size)

        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = "4/C", score = 76.0)))
        val graded = model(); advanceUntilIdle()
        assertEquals(UiText.Dynamic("4C"), graded.scores.hero.grade)
        assertEquals(RecordbookTone.OK, graded.scores.hero.gradeTone)
        assertNull(graded.scores.hero.next)
    }

    @Test fun `numbered checkpoints are grouped with their sum and lone ones share an untitled group`() = runTest {
        repository.controls = AppResult.Success(listOf(
            RecordbookControl(1, "Лабораторная работа 1", 8.0, 5.0, 10.0, true, null, null),
            RecordbookControl(2, "Лабораторная работа 2", 3.0, 5.0, 10.0, true, null, null),
            RecordbookControl(3, "Экзамен", null, 20.0, 40.0, true, null, null),
        ))
        val vm = model(); advanceUntilIdle()
        val (labs, rest) = vm.scores.groups
        assertEquals(UiText.Res(RecordbookStrings.recordbook_group_labs), labs.title)
        assertEquals(UiText.Res(RecordbookStrings.subject_group_sum, listOf(UiText.Dynamic("11"), "20")), labs.sum)
        assertTrue(labs.below)
        assertEquals(listOf(false, true), labs.rows.map { it.below })
        assertNull(rest.title)
        assertFalse(rest.rows.single().graded)
    }

    @Test fun `a connected sheet shows its total in the hero and its menu acts on it`() = runTest {
        val sheets = FakeSheetScoresRepository()
        repository.subjects = AppResult.Success(listOf(recordbookSubject().copy(rate = null, score = 35.0)))
        val vm = model(sheets = sheets); advanceUntilIdle()
        sheets.scores.value = listOf(sheetScore(scope = scopeOf(), value = "41,5"))
        advanceUntilIdle()

        assertEquals("41,5", vm.scores.hero.points)
        val line = vm.scores.sheet!!
        assertEquals(SheetScoresArgs.Step.TOTAL, line.changeTotal.step)
        vm.disconnectSheet(); advanceUntilIdle()
        assertEquals(listOf(scopeOf()), sheets.disconnected)
    }

    @Test fun `without a connection the sheet links are offered`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            shared = listOf(subjectLink("sheet", LinkCategory.SCORES, isMine = false).copy(url = sheetUrl("sheet"))),
        ))
        val vm = model(); advanceUntilIdle()
        val option = vm.scores.sheetOffer.single()
        assertEquals(SheetScoresArgs.Step.CONNECT, option.args.step)
        assertEquals(sheetUrl("sheet"), option.args.url)
    }

    // --- Пары

    @Test fun `an exact discipline id shows the lessons ahead and their teachers with roles`() = runTest {
        lessons.lessons.value = listOf(
            subjectLesson(1, "2026-09-08", subjectId = 1L, typeId = 1, teacherIsu = 100001, teacherFio = "Лекторов Лев Львович"),
            subjectLesson(2, "2026-09-09", subjectId = 1L, typeId = 3, teacherIsu = 100002, teacherFio = "Практиков Пётр Петрович"),
            subjectLesson(3, "2026-09-10", subjectId = 1L, typeId = 3, teacherIsu = 100002, teacherFio = "Практиков Пётр Петрович"),
            subjectLesson(4, "2026-09-11", subjectId = 2L, name = "Другой предмет"),
        )
        val vm = currentPeriodModel(); advanceUntilIdle()
        val tab = vm.lessonsTab
        val items = (tab.list as SubjectLessonList.Content).items
        assertEquals(listOf(1L, 2L, 3L), items.map { it.pairId })
        // The schedule's lesson card: the same time, place and target as a lesson of the day.
        val first = items.first().lesson
        assertEquals("09:30" to "11:00", first.start to first.end)
        assertTrue(first.place != null)
        assertEquals(1L, ((first.target as dev.alllexey.itmowidgets.core.lesson.LessonTarget.Lesson).args.pairId))
        assertEquals(listOf("Лекторов Л. Л.", "Практиков П. П."), tab.teachers.map { it.name })
        assertEquals(listOf(100001, 100002), tab.teachers.map { it.isu })
        assertEquals(UiText.Joined(listOf(UiText.Res(RecordbookStrings.subject_role_lectures)), ", "), tab.teachers.first().detail)
    }

    @Test fun `teacher rows say the tone in words, without tones a review is asked for, without the connection a gate`() = runTest {
        levels.levels[100001] = TeacherLevel.VERY_POSITIVE
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L, typeId = 2, teacherIsu = 100001, teacherFio = "Ершов Никита Андреевич"))
        val vm = currentPeriodModel(); advanceUntilIdle()
        val detail = vm.lessonsTab.teachers.single().detail as UiText.Joined
        assertEquals(UiText.Res("teacher_tone_very_positive"), detail.parts.last())
        assertEquals(SubjectReviews.Shown, vm.lessonsTab.reviews)

        levels.levels.clear()
        val few = currentPeriodModel(); advanceUntilIdle()
        assertEquals(SubjectReviews.Few(TeacherReviewArgs(100001, "Ершов Никита Андреевич")), few.lessonsTab.reviews)

        gate.optedIn.value = false; advanceUntilIdle()
        assertEquals(SubjectReviews.Off, few.lessonsTab.reviews)
    }

    @Test fun `teacher tones are asked once per set of ISUs`() = runTest {
        levels.levels[123456] = TeacherLevel.POSITIVE
        lessons.lessons.value = listOf(
            subjectLesson(1, "2026-09-08", subjectId = 1L, typeId = 1, teacherIsu = 123456, teacherFio = "Лектор Л. Л."),
            subjectLesson(2, "2026-09-10", subjectId = 1L, typeId = 3, teacherIsu = null, teacherFio = "Без ИСУ Б. Б."),
        )
        currentPeriodModel(); advanceUntilIdle()
        lessons.lessons.value = lessons.lessons.value + subjectLesson(3, "2026-09-11", subjectId = 1L, teacherIsu = 123456, teacherFio = "Лектор Л. Л.")
        advanceUntilIdle()
        assertEquals(listOf(setOf(123456)), levels.calls)
    }

    @Test fun `a name match is asked about, confirming stores it and rejecting drops the question`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 555L, name = "Тестовый  ПРЕДМЕТ", teacherFio = "Иванов И. И."))
        val vm = currentPeriodModel(); advanceUntilIdle()
        assertEquals(SubjectBinding(listOf(SubjectBindingCandidate(555L, "Тестовый  ПРЕДМЕТ"))), vm.lessonsTab.binding)
        assertEquals(SubjectLessonList.None, vm.lessonsTab.list)

        vm.rejectBinding(); advanceUntilIdle()
        assertNull(vm.lessonsTab.binding)
        assertEquals(SubjectLessonList.Unmatched, vm.lessonsTab.list)

        vm.confirmBinding(555L); advanceUntilIdle()
        assertEquals(mapOf(1L to 555L), bindingStore.bindings)
        assertEquals(listOf(1L), (vm.lessonsTab.list as SubjectLessonList.Content).items.map { it.pairId })
    }

    @Test fun `a failed schedule refresh is an error only when the cache is empty`() = runTest {
        scheduleRefresh.result = AppResult.Failure(AppError.Network)
        val vm = currentPeriodModel(); advanceUntilIdle()
        assertEquals(SubjectLessonList.Failed(AppError.Network), vm.lessonsTab.list)

        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L))
        vm.retryLessons(); advanceUntilIdle()
        assertTrue(vm.lessonsTab.list is SubjectLessonList.Content)
    }

    @Test fun `past periods have no lessons ahead but keep the recordbook teacher`() = runTest {
        lessons.lessons.value = listOf(subjectLesson(1, "2026-09-08", subjectId = 1L))
        val past = model(); advanceUntilIdle()
        assertEquals(SubjectLessonList.None, past.lessonsTab.list)
        assertEquals(listOf("Тестовый П."), past.lessonsTab.teachers.map { it.name })
        assertTrue(scheduleRefresh.requests.isEmpty())
    }

    @Test fun `three nearest lessons first and the rest after all lessons`() = runTest {
        lessons.lessons.value = (1L..5L).map { subjectLesson(it, "2026-09-${(7 + it).toString().padStart(2, '0')}", subjectId = 1L) }
        val vm = currentPeriodModel(); advanceUntilIdle()
        val first = vm.lessonsTab.list as SubjectLessonList.Content
        assertEquals(listOf(1L, 2L, 3L), first.items.map { it.pairId })
        assertEquals(2, first.more)

        vm.showAllLessons()
        val all = vm.lessonsTab.list as SubjectLessonList.Content
        assertEquals((1L..5L).toList(), all.items.map { it.pairId })
        assertEquals(0, all.more)
    }

    // --- Материалы

    @Test fun `not connected the materials are only a gate`() = runTest {
        gate.optedIn.value = false
        resources.state.value = SubjectLinksState.Content(linksSnapshot(mine = listOf(subjectLink("own")), servicesEnabled = false))
        val vm = model(); advanceUntilIdle()
        assertEquals(SubjectMaterials.Gate, vm.materials)
    }

    @Test fun `chats come first, then links by category with own links marked and the pinned one first`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            mine = listOf(
                subjectLink("table", LinkCategory.SCORES),
                subjectLink("pending", LinkCategory.TASKS).copy(status = SubjectLinkStatus.PENDING),
                subjectLink("chat", LinkCategory.CHAT),
            ),
            shared = listOf(
                subjectLink("group-chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false),
                subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false, score = 5),
                subjectLink("pinned", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false),
            ),
            pinnedId = "pinned",
        ))
        val vm = model(); advanceUntilIdle()
        val links = vm.links
        assertEquals(listOf("chat", "group-chat"), links.chats.map { it.id })
        assertTrue(links.chats.all { it.score == null })
        assertEquals(
            listOf(LinkCategory.SCORES.title(), LinkCategory.TASKS.title()),
            links.sections.map { it.title },
        )
        assertEquals(listOf("pinned", "table"), links.sections[0].rows.map { it.id })
        val pending = links.sections[1].rows.single { it.id == "pending" }
        assertTrue(pending.own)
        assertEquals(UiText.Res(RecordbookStrings.subject_link_pending), pending.status)
        assertFalse(pending.canVote)
        assertTrue(links.sections[1].rows.single { it.id == "tasks" }.canVote)
    }

    @Test fun `no links is the empty state with its add action`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot())
        val vm = model(); advanceUntilIdle()
        assertEquals(1L, (vm.materials as SubjectMaterials.Empty).args.subjectId)
    }

    @Test fun `votes toggle and follow restrictions`() = runTest {
        val shared = subjectLink("notes", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, score = 3)
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(shared)))
        val vm = model(); advanceUntilIdle()
        assertTrue(vm.links.sections.single().rows.single().canVote)

        vm.voteLink("notes", up = true); advanceUntilIdle()
        resources.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(shared.copy(myVote = 1))))
        advanceUntilIdle()
        vm.voteLink("notes", up = true); advanceUntilIdle()
        assertEquals(listOf("vote:notes:1", "vote:notes:0"), resources.actions)

        resources.restrictions.value = listOf(UserRestriction("r", RestrictionCapability.VOTE, "spam", null))
        advanceUntilIdle()
        assertFalse(vm.links.sections.single().rows.single().canVote)
    }

    @Test fun `a vote that fails is reported once`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(
            shared = listOf(subjectLink("notes", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false)),
        ))
        resources.result = AppResult.Failure(AppError.Network)
        val vm = model(); advanceUntilIdle()
        val errors = mutableListOf<AppError>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { errors += (it as RecordbookSubjectEvent.VoteFailed).error }
        }
        vm.voteLink("notes", up = false); advanceUntilIdle()
        assertEquals(listOf<AppError>(AppError.Network), errors)
        collector.cancel()
    }

    @Test fun `links of earlier semesters wait behind all links`() = runTest {
        resources.state.value = SubjectLinksState.Content(linksSnapshot(previous = listOf(subjectLink("old", LinkCategory.NOTES, isMine = false))))
        val vm = model(); advanceUntilIdle()
        assertEquals(1, vm.links.previous)
        assertTrue(vm.links.sections.isEmpty())
    }

    private fun scopeOf() = dev.alllexey.itmowidgets.core.resources.ResourceScope(1, "Тестовый предмет", "2025-2")

    private fun sheetUrl(id: String) = "https://docs.google.com/spreadsheets/d/1SyntheticSheet${id.padEnd(16, '0')}/edit"
}
