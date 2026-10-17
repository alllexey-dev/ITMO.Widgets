package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.home.FakeBellRepository
import dev.alllexey.itmowidgets.feature.home.bellItem
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class BellViewModelTest {

    private val main = TestMainDispatcher()
    private val time = FixedAcademicTime(LocalDateTime(LocalDate(2026, 9, 7), LocalTime(15, 0)))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun openingReadsEverythingAndKeepsTheUnseenHighlightedForThisOpening() = runTest(main.dispatcher) {
        val bell = FakeBellRepository(bellItem("new"), bellItem("old", at = Instant.parse("2026-09-06T09:00:00Z")), seen = setOf("old"))
        val vm = BellViewModel(bell, time)

        val state = vm.uiState.first { !it.loading }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("new"), state.fresh.map { it.item.id })
        assertEquals(listOf("old"), state.earlier.map { it.item.id })
        assertEquals(UiText.Dynamic("12:05"), state.fresh.single().time)
        assertEquals(UiText.Res(CoreStrings.event_moment_yesterday), state.earlier.single().time)
        assertEquals(setOf("new", "old"), bell.seen.value)
        assertEquals(1, bell.readAllCalls)
        assertEquals(1, bell.refreshCalls)
        assertEquals(listOf("new"), vm.uiState.value.fresh.map { it.item.id })
    }

    @Test
    fun anEventThatArrivesWhileTheSheetIsOpenIsNewToo() = runTest(main.dispatcher) {
        val bell = FakeBellRepository(seen = emptySet())
        val vm = BellViewModel(bell, time)
        vm.uiState.first { !it.loading }

        bell.items.value = listOf(bellItem("late"))
        val state = vm.uiState.first { it.fresh.isNotEmpty() }

        assertEquals(listOf("late"), state.fresh.map { it.item.id })
        assertEquals(setOf("late"), bell.seen.value)
        assertEquals(1, bell.readAllCalls)
    }

    @Test
    fun aRowOpensItsHomeAndAFailedAnswerSaysWhy() = runTest(main.dispatcher) {
        val target = EntryRoute(AppTab.SPORT)
        val bell = FakeBellRepository()
        bell.answer = AppResult.Failure(AppError.Network)
        val vm = BellViewModel(bell, time)

        vm.open(bellItem("sport").copy(target = target))
        assertEquals(BellEvent.Open(target), vm.events.first())

        vm.answer(bellItem("friend", at = null, request = 42), accept = true)
        assertEquals(BellEvent.AnswerFailed(AppError.Network), vm.events.first())
        assertEquals(listOf(42 to true), bell.answers)
    }
}
