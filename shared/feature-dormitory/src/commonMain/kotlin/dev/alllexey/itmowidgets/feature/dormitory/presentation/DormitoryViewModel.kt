package dev.alllexey.itmowidgets.feature.dormitory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.dormitory.domain.DormitoryRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The `Общежитие` screen: the stored answer at once, a refresh from my.itmo on creation, and the stored answer with
 * its age when that refresh fails; [time] gives the Moscow day the Платежи are classified on. Read-only: dates,
 * documents and payments are handled in my.itmo ([DormitoryWeb]).
 */
class DormitoryViewModel(
    private val repository: DormitoryRepository,
    private val clock: Clock,
    private val time: AcademicTimeProvider,
) : ViewModel() {

    private val refreshes = RefreshTracker(viewModelScope)

    /** The last refresh's failure; null while none failed or after one succeeded. */
    private val failure = MutableStateFlow<AppError?>(null)

    private val pastYearsExpanded = MutableStateFlow(false)

    val uiState: StateFlow<DormitoryUiState> = combine(
        repository.observe().onStart { emit(null) },
        failure,
        refreshes.refreshing,
        pastYearsExpanded,
    ) { answer, error, refreshing, expanded ->
        DormitoryPresenter.present(answer, error, refreshing, clock.now(), time.today(), expanded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DormitoryUiState())

    init {
        refresh(RefreshMode.Silent)
    }

    /** Folds or unfolds `Прошлые годы`. */
    fun togglePastYears() {
        pastYearsExpanded.update { !it }
    }

    /** A pull, or `Повторить` with [RefreshMode.Force]. */
    fun refresh(mode: RefreshMode = RefreshMode.Pull) {
        refreshes.launch(mode) {
            failure.value = (repository.refresh() as? AppResult.Failure)?.error
        }
    }
}
