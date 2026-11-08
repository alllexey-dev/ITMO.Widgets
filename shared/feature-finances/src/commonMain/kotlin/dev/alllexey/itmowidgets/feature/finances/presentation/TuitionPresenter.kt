package dev.alllexey.itmowidgets.feature.finances.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.ContractList
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.text.updatedAgo
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.finances.domain.TuitionAnswer

/** Builds every text of the `Оплата обучения` screen, so Compose and SwiftUI only lay it out. */
internal object TuitionPresenter {

    /**
     * [answer] is the stored one (null before the first), [failure] the last refresh's error: with an answer it only
     * adds the age, without one it is the screen. [time] gives the Moscow day the Платежи are classified on;
     * [pastYearsExpanded] unfolds `Прошлые годы`.
     */
    fun present(
        answer: TuitionAnswer?,
        failure: AppError?,
        refreshing: Boolean,
        time: AcademicTimeProvider,
        pastYearsExpanded: Boolean = false,
    ): TuitionUiState {
        if (answer == null) {
            val content = failure?.let { TuitionContent.Failed(it.toUiText()) } ?: TuitionContent.Loading
            return TuitionUiState(content, refreshing)
        }
        val contracts = ContractList.of(answer.contracts, time.today(), pastYearsExpanded)
        return TuitionUiState(
            content = contracts?.let(TuitionContent::Shown) ?: TuitionContent.NoContracts,
            refreshing = refreshing,
            stale = failure?.let { updatedAgo(answer.fetchedAt, time.now()) },
        )
    }
}
