package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * The bell's sheet. [fresh] are the events new at this opening (a dot and a bold title), [earlier] the rest; without
 * fresh ones the sheet says `Ничего нового` over [earlier]. Nothing at all is [empty]. [loading] lasts until every
 * source has answered once.
 */
data class BellUiState(
    val loading: Boolean = true,
    val fresh: List<BellRow> = emptyList(),
    val earlier: List<BellRow> = emptyList(),
) {
    val empty: Boolean get() = !loading && fresh.isEmpty() && earlier.isEmpty()
}

/** One event; [time] is `12:05` today, `вчера` or `7 окт.`; [busy] while its friend request is being answered. */
data class BellRow(val item: BellItem, val new: Boolean, val time: UiText? = null, val busy: Boolean = false)

sealed interface BellEvent {
    /** A row leads to its home: close the sheet and open [route]. */
    data class Open(val route: EntryRoute) : BellEvent

    data class AnswerFailed(val error: AppError) : BellEvent
}
