package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.feature.home.FakeDayLessonsGateway
import dev.alllexey.itmowidgets.feature.home.dayLesson
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class TodayViewModelTest {

    private val main = TestMainDispatcher()
    private val monday = LocalDate(2026, 9, 7)
    private val time = FixedAcademicTime(LocalDateTime(monday, LocalTime(12, 22)))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theFirstShowRefreshesTodayAndTomorrowOnce() = runTest(main.dispatcher) {
        val gateway = FakeDayLessonsGateway(LessonDay(monday, listOf(dayLesson("11:40", "13:10")), null))
        val vm = TodayViewModel(gateway, emptyList(), time)

        vm.ensureDataLoaded()
        vm.ensureDataLoaded()
        val state = vm.uiState.first { it is TodayUiState.Content && it.hero != null } as TodayUiState.Content

        assertEquals(listOf(monday to LocalDate(2026, 9, 8)), gateway.refreshes)
        assertEquals("Физика", state.hero!!.subject)
    }

    @Test
    fun aFailedFirstRefreshWithoutCacheShowsTheErrorAndARetryLoads() = runTest(main.dispatcher) {
        val gateway = FakeDayLessonsGateway()
        gateway.result = AppResult.Failure(AppError.Network)
        val vm = TodayViewModel(gateway, emptyList(), time)

        vm.ensureDataLoaded()
        assertIs<TodayUiState.Failed>(vm.uiState.first { it is TodayUiState.Failed })

        gateway.result = AppResult.Success(Unit)
        gateway.days.value = listOf(LessonDay(monday, listOf(dayLesson("15:20", "16:50")), null))
        vm.refresh(RefreshMode.Force)

        val content = vm.uiState.first { it is TodayUiState.Content } as TodayUiState.Content
        assertEquals(2, gateway.refreshes.size)
        assertEquals("15:20–16:50", content.hero!!.time)
    }

    @Test
    fun everyTodoSourceAddsItsThingsToDo() = runTest(main.dispatcher) {
        val todo = TodayTodo("bars", AppIcon.LOGIN, UiText.Dynamic("t"), UiText.Dynamic("r"), UiText.Dynamic("a"), EntryRoute(AppTab.HOME))
        val gateway = FakeDayLessonsGateway(LessonDay(monday, emptyList(), null))
        val vm = TodayViewModel(gateway, listOf(TodayTodoSource { flowOf(listOf(todo)) }), time)

        vm.ensureDataLoaded()
        val state = vm.uiState.first { it is TodayUiState.Content && it.todos.isNotEmpty() } as TodayUiState.Content

        assertEquals(listOf(todo), state.todos)
    }
}
