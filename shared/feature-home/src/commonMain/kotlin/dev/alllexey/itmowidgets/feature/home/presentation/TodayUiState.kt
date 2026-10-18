package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.today.TodayTodo

sealed interface TodayUiState {
    /** Nothing cached and the first refresh still runs. */
    data object Loading : TodayUiState

    /** Nothing cached and the refresh failed: `Не удалось загрузить` with `Повторить`. */
    data class Failed(val error: AppError, val retrying: Boolean) : TodayUiState

    /**
     * The [hero] (the lesson to go to now, or tomorrow's first in the evening), the [day] as a timeline and
     * `Нужно сделать` ([todos], only when there is something). Without a hero and a day there are no lessons today and
     * tomorrow. [stale] (`Данные на 11:44`) dims the day after a failed refresh; [refreshing] is a pull's indicator.
     */
    data class Content(
        val hero: TodayHero?,
        val day: TodayDay?,
        val todos: List<TodayTodo>,
        val stale: UiText? = null,
        val refreshing: Boolean = false,
    ) : TodayUiState
}

/**
 * The lesson to go to now: [label] (`Сейчас`, `Первая пара`, `Далее`, `Завтра`) and [countdown] (`ещё 48 мин`,
 * `через 15 мин`, `в 10:00`) over the [subject] and [byline]; the big [room] with the building's full [address], or
 * `Онлайн` with `Войти` for [videoUrl]; [time] `10:00–11:30`, the current lesson's elapsed [progress] between
 * [start] and [end]; the lesson's [note]; [route] the campus map's «Маршрут» to an in-person lesson.
 */
data class TodayHero(
    val label: UiText,
    val countdown: UiText,
    val subject: String,
    val byline: UiText,
    val room: UiText?,
    val address: String?,
    val online: Boolean,
    val videoUrl: String?,
    val start: String,
    val end: String,
    val progress: Float?,
    val note: String?,
    val target: LessonTarget,
    val route: AppRoute? = null,
) {
    val time: String get() = "$start–$end"
}

/** The day under the hero: [title] (`Пары` or `Завтра`), [count] (`5 пар`), its [items]; [showNow] for today. */
data class TodayDay(val title: UiText, val count: UiText, val items: List<TimelineItem>, val showNow: Boolean)
