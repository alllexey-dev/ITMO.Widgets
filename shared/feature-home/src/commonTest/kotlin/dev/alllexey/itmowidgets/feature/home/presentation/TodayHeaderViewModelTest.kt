package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.settings.HomePassVisibility
import kotlinx.coroutines.flow.flowOf
import dev.alllexey.itmowidgets.core.testing.FakeAppForeground
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class TodayHeaderViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theHeaderNamesTheTabOverTheAcademicDateAndCountsTheUnseenEvents() = runTest(main.dispatcher) {
        val bell = FakeBellRepository(bellItem("a"), bellItem("b"), bellItem("c"), seen = setOf("a"))
        val vm = TodayHeaderViewModel(FixedAcademicTime(LocalDate(2026, 9, 7)), bell, FakeAppForeground(), HomePassVisibility { flowOf(true) })

        assertEquals(
            TodayHeaderUiState(UiText.Res(CoreStrings.tab_today), UiText.Dynamic("понедельник, 7 сентября"), badge = 2),
            vm.uiState.first { it.badge == 2 },
        )
    }

    @Test
    fun thePassFollowsTheStoredChoiceWithoutChangingTheBell() = runTest(main.dispatcher) {
        val visible = MutableStateFlow(false)
        val vm = TodayHeaderViewModel(
            FixedAcademicTime(LocalDate(2026, 9, 7)), FakeBellRepository(), FakeAppForeground(),
            HomePassVisibility { visible },
        )
        assertEquals(false, vm.uiState.first { !it.showPass }.showPass)
        visible.value = true
        assertEquals(true, vm.uiState.first { it.showPass }.showPass)
        visible.value = false
        assertEquals(false, vm.uiState.first { !it.showPass }.showPass)
    }

    @Test
    fun theBellsRemoteSourcesAreAskedOnCreationAndOnEachReturnToTheForeground() = runTest(main.dispatcher) {
        val bell = FakeBellRepository()
        val foreground = FakeAppForeground()
        TodayHeaderViewModel(FixedAcademicTime(LocalDate(2026, 9, 7)), bell, foreground, HomePassVisibility { flowOf(true) })
        testScheduler.advanceUntilIdle()
        assertEquals(1, bell.refreshCalls)

        foreground.comeBack()
        testScheduler.advanceUntilIdle()

        assertEquals(2, bell.refreshCalls)
    }
}
