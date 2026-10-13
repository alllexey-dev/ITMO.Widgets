package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/**
 * Owns the Сегодня header on both platforms: the date and the bell's badge, the events the user has not seen.
 * [refresh] re-reads the date and asks the bell's remote sources when the tab comes back.
 */
class TodayHeaderViewModel(private val time: AcademicTimeProvider, private val bell: BellRepository) : ViewModel() {
    private val date = MutableStateFlow(time.today())

    val uiState: StateFlow<TodayHeaderUiState> = combine(date, bell.observeUnseenCount()) { today, unseen ->
        stateOf(today, unseen)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), stateOf(time.today(), 0))

    fun refresh() {
        date.value = time.today()
        viewModelScope.launch { bell.refresh() }
    }

    private fun stateOf(date: LocalDate, badge: Int) = TodayHeaderUiState(
        title = UiText.Res(AppTab.HOME.label),
        subtitle = UiText.Dynamic(date.format(DateTexts.WEEKDAY_DAY_MONTH)),
        badge = badge,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
