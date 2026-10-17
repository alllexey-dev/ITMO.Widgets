package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.platform.AppForeground
import dev.alllexey.itmowidgets.core.settings.HomePassVisibility
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/**
 * Owns the Сегодня header on both platforms: the date and the bell's badge, the events the user has not seen.
 * It re-reads the date and asks the bell's remote sources once when it is created and on each return of the app to the
 * foreground ([AppForeground]), not when a sheet closes over the tab: the bell's own sheet marks events seen through
 * the repository, which the badge follows.
 */
class TodayHeaderViewModel(
    private val time: AcademicTimeProvider,
    private val bell: BellRepository,
    foreground: AppForeground,
    passVisibility: HomePassVisibility,
) : ViewModel() {
    private val date = MutableStateFlow(time.today())

    val uiState: StateFlow<TodayHeaderUiState> = combine(date, bell.observeUnseenCount(), passVisibility.observeHomePassVisible()) { today, unseen, showPass ->
        stateOf(today, unseen, showPass)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), stateOf(time.today(), 0, false))

    init {
        refresh()
        foreground.returns().onEach { refresh() }.launchIn(viewModelScope)
    }

    private fun refresh() {
        date.value = time.today()
        viewModelScope.launch { bell.refresh() }
    }

    private fun stateOf(date: LocalDate, badge: Int, showPass: Boolean) = TodayHeaderUiState(
        title = UiText.Res(AppTab.HOME.label),
        subtitle = UiText.Dynamic(date.format(DateTexts.WEEKDAY_DAY_MONTH)),
        badge = badge,
        showPass = showPass,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
