package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import dev.alllexey.itmowidgets.shared.feature.home.HomeStrings
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The bell's sheet; one instance per opening. Opening reads everything: the events the user had not seen are marked
 * seen at once (the badge goes) and every source reads its own (dots, notifications), while this opening keeps them
 * highlighted. There is no `Прочитать все`. Events that arrive while the sheet is open are new as well.
 */
class BellViewModel(private val bell: BellRepository, private val time: AcademicTimeProvider) : ViewModel() {

    private val highlighted = MutableStateFlow<Set<String>?>(null)
    private val busy = BusyKeys<Int>(viewModelScope)
    private val queue = EventQueue<BellEvent>()

    val events: Flow<BellEvent> = queue.events

    val uiState: StateFlow<BellUiState> = combine(bell.observeItems(), highlighted, busy.busy) { items, fresh, answering ->
        if (fresh == null) return@combine BellUiState()
        val today = time.today()
        val rows = items.map { item ->
            BellRow(item, new = item.id in fresh, time = item.at?.let { moment(it, today) }, busy = item.request?.let(answering::contains) == true)
        }
        BellUiState(loading = false, fresh = rows.filter { it.new }, earlier = rows.filterNot { it.new })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BellUiState())

    init {
        viewModelScope.launch {
            val seen = bell.observeSeen().first()
            var first = true
            bell.observeItems().collect { items ->
                val unseen = items.map { it.id }.filterTo(mutableSetOf()) { it !in seen && it !in highlighted.value.orEmpty() }
                highlighted.update { it.orEmpty() + unseen }
                bell.markSeen(unseen)
                if (first) {
                    first = false
                    bell.readAll()
                }
            }
        }
        viewModelScope.launch { bell.refresh() }
    }

    fun open(item: BellItem) {
        val route = item.target ?: return
        viewModelScope.launch { queue.send(BellEvent.Open(route)) }
    }

    /** `Принять` or `Отклонить`; the row stays busy until Backend answers, a failure says why. */
    fun answer(item: BellItem, accept: Boolean) {
        val isu = item.request ?: return
        busy.launch(isu) {
            val result = bell.answer(isu, accept)
            if (result is AppResult.Failure) queue.send(BellEvent.AnswerFailed(result.error))
        }
    }

    /** `12:05` today, `вчера`, or `7 окт.`. */
    private fun moment(at: Instant, today: LocalDate): UiText {
        val local = at.toLocalDateTime(time.timeZone)
        return when (local.date) {
            today -> UiText.Dynamic(local.time.format(DateTexts.TIME))
            today.minus(1, DateTimeUnit.DAY) -> UiText.Res(HomeStrings.bell_yesterday)
            else -> UiText.Dynamic("${local.date.format(DateTexts.DAY)} ${local.date.format(DateTexts.MONTH_SHORT)}")
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
