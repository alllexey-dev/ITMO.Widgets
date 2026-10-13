package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class TodayHeaderViewModelTest {

    @Test
    fun theHeaderNamesTheTabOverTheAcademicDate() {
        val vm = TodayHeaderViewModel(FixedAcademicTime(LocalDate(2026, 9, 7)))

        assertEquals(
            TodayHeaderUiState(UiText.Res(CoreStrings.tab_today), UiText.Dynamic("понедельник, 7 сентября")),
            vm.uiState.value,
        )
    }
}
