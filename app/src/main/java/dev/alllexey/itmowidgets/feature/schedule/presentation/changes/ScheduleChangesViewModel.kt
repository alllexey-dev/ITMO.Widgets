package dev.alllexey.itmowidgets.feature.schedule.presentation.changes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The history of schedule changes. While the screen is visible every unread change is marked read, and the rows that
 * were unread keep their "new" dot for the life of the screen, recreation included.
 */
@HiltViewModel
class ScheduleChangesViewModel @Inject constructor(
    private val repository: ScheduleChangesRepository,
    private val timeProvider: AcademicTimeProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val newIds = MutableStateFlow(savedStateHandle.get<ArrayList<String>>(KEY_NEW_IDS).orEmpty().toSet())
    private val changes: StateFlow<List<ScheduleChange>?> =
        repository.observeChanges().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<ScheduleChangesUiState> = combine(changes, newIds, visible) { changes, newIds, visible ->
        // A visible unread row is new before the read mark lands, so the dot does not blink in a frame later.
        changes?.let { render(it) { change -> change.id in newIds || (visible && !change.read) } }
            ?: ScheduleChangesUiState.Loading
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScheduleChangesUiState.Loading)

    init {
        viewModelScope.launch {
            combine(changes.filterNotNull(), visible, ::Pair).collect { (changes, visible) ->
                if (visible) markRead(changes)
            }
        }
    }

    /** The fragment reports `onStart`/`onStop`; a screen hidden under another overlay marks nothing. */
    fun setVisible(visible: Boolean) {
        this.visible.value = visible
    }

    private suspend fun markRead(changes: List<ScheduleChange>) {
        val unread = changes.filterNot(ScheduleChange::read).map(ScheduleChange::id)
        if (unread.isEmpty()) return
        newIds.value += unread
        savedStateHandle[KEY_NEW_IDS] = ArrayList(newIds.value)
        repository.markAllRead()
    }

    private fun render(changes: List<ScheduleChange>, isNew: (ScheduleChange) -> Boolean): ScheduleChangesUiState {
        if (changes.isEmpty()) return ScheduleChangesUiState.Empty
        val today = timeProvider.today()
        val days = changes.groupBy { it.detectedAt.atZone(timeProvider.zoneId).toLocalDate() }
            .toSortedMap(reverseOrder())
            .map { (date, dayChanges) ->
                val relative = when (date) {
                    today -> RelativeDay.TODAY
                    today.minusDays(1) -> RelativeDay.YESTERDAY
                    else -> if (date.year == today.year) RelativeDay.OTHER else RelativeDay.OTHER_YEAR
                }
                val rows = dayChanges.sortedWith(ROW_ORDER).map { ScheduleChangeRow(it, isNew(it)) }
                ScheduleChangeDay(date, relative, rows)
            }
        return ScheduleChangesUiState.Content(days)
    }

    private companion object {
        const val KEY_NEW_IDS = "new_ids"
        val ROW_ORDER: Comparator<ScheduleChange> =
            compareByDescending<ScheduleChange> { it.detectedAt }.thenBy { it.soonestStart() }.thenBy { it.id }
    }
}
