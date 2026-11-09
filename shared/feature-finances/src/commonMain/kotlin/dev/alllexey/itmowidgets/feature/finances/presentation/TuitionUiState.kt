package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.text.ContractList
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * The `Оплата обучения` screen (docs/features/finances.md). [stale] is `Обновлено 2 ч назад` over the stored answer
 * after a refresh failed; [refreshing] a pull in flight.
 */
data class TuitionUiState(
    val content: TuitionContent = TuitionContent.Loading,
    val refreshing: Boolean = false,
    val stale: UiText? = null,
)

sealed interface TuitionContent {
    /** The first answer is on its way and none is stored. */
    data object Loading : TuitionContent

    /** Nothing stored and the refresh failed: [text] says why, the screen offers `Повторить`. */
    data class Failed(val text: UiText) : TuitionContent

    /** my.itmo knows no Договор об обучении: a budget place. */
    data object NoContracts : TuitionContent

    /** The Договоры об обучении, earlier years folded into `Прошлые годы`. */
    data class Shown(val contracts: ContractList) : TuitionContent
}
