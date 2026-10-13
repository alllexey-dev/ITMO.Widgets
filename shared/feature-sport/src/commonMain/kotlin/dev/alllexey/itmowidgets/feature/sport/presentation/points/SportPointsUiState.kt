package dev.alllexey.itmowidgets.feature.sport.presentation.points

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportScoreUi
import dev.alllexey.itmowidgets.shared.feature.sport.SportPlurals
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** `Откуда баллы` (DECISIONS.md, Спорт): the formula, the bonus cap and My ITMO's history of awards by date. */
sealed interface SportPointsUiState {
    data object Loading : SportPointsUiState

    data class Failed(val error: AppError) : SportPointsUiState

    data class Content(
        val score: SportScoreUi,
        /** `ещё 70` or `зачёт`, the pill over the formula. */
        val status: UiText,
        /** `3 посещения`, under the visits of the formula. */
        val visitsLabel: UiText,
        /** `15 из 40`: the bonus in the total of its cap. */
        val cap: UiText,
        val capShare: Float,
        /** `Зачёт набран 28 октября`, once the history reaches 100. */
        val done: UiText?,
        val filter: SportPointsFilter,
        val chips: List<SportPointsChip>,
        val months: List<SportPointsMonth>,
        val refreshing: Boolean = false,
    ) : SportPointsUiState
}

enum class SportPointsFilter { ALL, VISITS, BONUS }

/** `Все`, `Посещения 13`, `Бонусы 2`: the [count] of awards. */
data class SportPointsChip(val filter: SportPointsFilter, val label: UiText, val count: Int?, val selected: Boolean)

/** The awards of one month, newest first: `Сентябрь`. */
data class SportPointsMonth(val title: UiText, val rows: List<SportPointsRow>)

/** One award: what ([title]), when and the place won ([subtitle]), the [points] `+5`; [bonus] for anything but a visit. */
data class SportPointsRow(val title: String, val subtitle: UiText, val points: String, val bonus: Boolean)

object SportPointsPresenter {

    const val BONUS_CAP = 40
    private const val CREDIT = 100
    private const val VISIT = "lesson"

    fun present(score: SportScore, filter: SportPointsFilter, zone: TimeZone): SportPointsUiState.Content {
        val summary = score.summary
        val history = score.attendancesData.sortedByDescending { it.dateTime }
        val visits = history.count { it.isVisit() }
        val bonuses = history.size - visits
        val shown = history.filter {
            when (filter) {
                SportPointsFilter.ALL -> true
                SportPointsFilter.VISITS -> it.isVisit()
                SportPointsFilter.BONUS -> !it.isVisit()
            }
        }
        return SportPointsUiState.Content(
            score = SportScoreUi.of(summary),
            status = if (summary.remaining == 0) {
                UiText.Res(SportStrings.sport_points_status_done)
            } else {
                UiText.Res(SportStrings.sport_points_status_left, listOf(summary.remaining))
            },
            visitsLabel = UiText.Plural(SportPlurals.sport_points_visits, visits, listOf(visits)),
            cap = UiText.Res(SportStrings.sport_points_cap_value, listOf(summary.creditedBonus, BONUS_CAP)),
            capShare = summary.creditedBonus / BONUS_CAP.toFloat(),
            done = creditDate(history, zone)?.let { UiText.Res(SportStrings.sport_points_done_title, listOf(it)) },
            filter = filter,
            chips = listOf(
                SportPointsChip(SportPointsFilter.ALL, UiText.Res(SportStrings.sport_points_all), null, filter == SportPointsFilter.ALL),
                SportPointsChip(SportPointsFilter.VISITS, UiText.Res(SportStrings.sport_points_visits_chip), visits, filter == SportPointsFilter.VISITS),
                SportPointsChip(SportPointsFilter.BONUS, UiText.Res(SportStrings.sport_points_bonus_chip), bonuses, filter == SportPointsFilter.BONUS),
            ),
            months = shown.groupBy { it.dateTime.toLocalDateTime(zone).let { local -> local.year to local.month } }
                .map { (key, rows) ->
                    SportPointsMonth(
                        title = UiText.Dynamic(DateTexts.standaloneMonth(key.second).replaceFirstChar { it.uppercaseChar() }),
                        rows = rows.map { it.row(zone) },
                    )
                },
        )
    }

    /** The day the visits and the credited bonus first added up to 100; null below it or without a history. */
    private fun creditDate(history: List<SportAttendance>, zone: TimeZone): String? {
        var visits = 0
        var bonus = 0
        history.sortedBy { it.dateTime }.forEach {
            if (it.isVisit()) visits += it.score else bonus += it.score
            if (SportScoreSummary(visits, bonus).total >= CREDIT) {
                return it.dateTime.toLocalDateTime(zone).date.format(DateTexts.DAY_MONTH)
            }
        }
        return null
    }

    private fun SportAttendance.row(zone: TimeZone): SportPointsRow {
        val date = UiText.Dynamic(dateTime.toLocalDateTime(zone).date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH))
        val title = competitionName ?: name?.shorten() ?: evaluationName ?: ""
        return SportPointsRow(
            title = title.ifEmpty { evaluationName.orEmpty() },
            subtitle = place?.let { UiText.Joined(listOf(date, UiText.Dynamic(it)), ", ") } ?: date,
            points = "+$score",
            bonus = !isVisit(),
        )
    }

    private fun SportAttendance.isVisit() = type == VISIT
}

