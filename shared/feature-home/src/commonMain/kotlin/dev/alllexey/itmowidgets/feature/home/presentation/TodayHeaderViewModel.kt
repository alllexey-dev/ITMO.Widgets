package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/** The Сегодня root header: the tab's name over the academic date, «понедельник, 7 сентября». */
data class TodayHeaderUiState(val title: UiText, val subtitle: UiText)

/** Owns the Сегодня header on both platforms; [refresh] re-reads the date when the tab comes back. */
class TodayHeaderViewModel(private val time: AcademicTimeProvider) : ViewModel() {
    private val state = MutableStateFlow(stateOf(time.today()))
    val uiState: StateFlow<TodayHeaderUiState> = state.asStateFlow()

    fun refresh() {
        state.value = stateOf(time.today())
    }

    private fun stateOf(date: LocalDate) = TodayHeaderUiState(
        title = UiText.Res(AppTab.HOME.label),
        subtitle = UiText.Dynamic(date.format(DateTexts.WEEKDAY_DAY_MONTH)),
    )
}
