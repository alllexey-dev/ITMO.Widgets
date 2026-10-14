package dev.alllexey.itmowidgets.feature.sport.presentation.my

import dev.alllexey.itmowidgets.core.lesson.LessonUi
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings

/**
 * `Моё` of the Спорт tab (docs/features/sport.md): the score ring that leads to `Откуда баллы`, the bookings and
 * queues as lesson cards by day with `Сделать секцией` on the booked ones, the user's [sections] with their next
 * dates, and the question before a cancellation ([cancel]).
 */
data class SportMyUiState(
    val score: SportScoreUi? = null,
    val body: SportMyBody = SportMyBody.Loading,
    val sections: List<SportSectionCardUi> = emptyList(),
    val cancel: SportCancelPrompt? = null,
)

sealed interface SportMyBody {
    /** Nothing loaded yet. */
    data object Loading : SportMyBody

    /** Nothing loaded and the load failed: `Не удалось загрузить` with `Повторить`. */
    data class Failed(val error: AppError) : SportMyBody

    /**
     * Bookings and queues by day, nearest first; empty shows `Записей нет` with the way to `Запись`. [refreshing] while
     * a pull runs; [partialError] when a source failed behind the shown data.
     */
    data class Content(
        val days: List<SportBookingDay>,
        val refreshing: Boolean = false,
        val partialError: Boolean = false,
    ) : SportMyBody
}

/** One day of bookings under its label: `Сегодня, 7 сент.`. */
data class SportBookingDay(val title: UiText, val bookings: List<SportBookingItem>)

/** A booking or a queue as a lesson card; [lessonId] opens its sheet; a booked one has its [section] switch. */
data class SportBookingItem(val lessonId: Long, val lesson: LessonUi, val section: SportSectionToggleUi? = null)

/**
 * The points of the semester in a ring: [visits] and the credited [bonus] add up to [total] out of 100; [formula] is
 * `посещения 15, бонусы 15`, [status] `до зачёта ещё 70` or `зачёт набран` ([done]).
 */
data class SportScoreUi(
    val total: Int,
    val visits: Int,
    val bonus: Int,
    val formula: UiText,
    val status: UiText,
    val done: Boolean,
) {
    /** The visits' share of the ring, 0..1 of 100 points. */
    val visitsShare: Float get() = visits.coerceIn(0, MAX) / MAX.toFloat()

    /** The bonus' share of the ring after the visits. */
    val bonusShare: Float get() = bonus.coerceIn(0, MAX - visits.coerceIn(0, MAX)) / MAX.toFloat()

    companion object {
        private const val MAX = 100

        fun of(summary: SportScoreSummary): SportScoreUi = SportScoreUi(
            total = summary.total,
            visits = summary.attendances,
            bonus = summary.creditedBonus,
            formula = UiText.Res(SportStrings.sport_score_formula, listOf(summary.attendances, summary.creditedBonus)),
            status = if (summary.remaining == 0) {
                UiText.Res(SportStrings.sport_score_done)
            } else {
                UiText.Res(SportStrings.sport_score_remaining, listOf(summary.remaining))
            },
            done = summary.remaining == 0,
        )
    }
}

/** `Отменить запись?` or `Выйти из очереди?` before [lessonId]'s booking is cancelled; [confirm] names the button. */
data class SportCancelPrompt(val lessonId: Long, val message: UiText, val confirm: UiText)

/** A cancellation failed; the holder keeps it until the tab collects it. */
sealed interface SportMyEvent {
    data class ShowError(val error: AppError) : SportMyEvent
}
