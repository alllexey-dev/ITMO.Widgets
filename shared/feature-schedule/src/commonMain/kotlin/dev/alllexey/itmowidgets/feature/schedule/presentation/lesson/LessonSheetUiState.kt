package dev.alllexey.itmowidgets.feature.schedule.presentation.lesson

import dev.alllexey.itmowidgets.core.lesson.LessonPill
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.text.ScheduleChangeLine
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * The lesson sheet (`Лист пары`, docs/features/schedule.md): [over] `Практика, ср, 9 сентября` with the [status] pill,
 * the [subject], the big [room] (or `Онлайн` when [online]) with the [time] `10:00–11:30, 90 мин` and the building's
 * full [address]; the relevant [actions] ([actionsPending] while they may still change, the row keeps its place);
 * then the [changes], the [note] and the meeting [password], the [teacher],
 * the [flow], the [sport] row and the [friends] on the lesson.
 */
data class LessonSheetUiState(
    val over: UiText,
    val status: LessonPill?,
    val subject: String,
    val room: UiText?,
    val online: Boolean,
    val time: UiText,
    val address: String?,
    val actions: List<LessonAction>,
    val actionsPending: Boolean,
    val changes: LessonSheetChanges?,
    val note: String?,
    val password: String?,
    val teacher: LessonTeacher?,
    val flow: String?,
    val sport: LessonSport?,
    val friends: List<UserSummary>,
)

/** What an action tile does: `Маршрут` opens the campus map, the rest as named. */
enum class LessonActionKind { ROUTE, VIDEO, FRIENDS, LINKS, SUBJECT }

/**
 * An action tile: [label] `Друзья 2`; [url] the meeting of [LessonActionKind.VIDEO], [route] what the others open;
 * not [enabled] while there is nothing to open (`Друзья` while they load or with nobody on the lesson).
 */
data class LessonAction(
    val kind: LessonActionKind,
    val label: UiText,
    val url: String? = null,
    val route: AppRoute? = null,
    val enabled: Boolean = true,
)

/**
 * The lesson's newest change: [summary] for an added or cancelled lesson, else a [lines] `было → стало` per field;
 * [note] `Изменено вчера в 20:14`.
 */
data class LessonSheetChanges(val summary: UiText?, val lines: List<ScheduleChangeLine>, val note: UiText)

/** The teacher's full [name], the [isu] their card opens with, the review [tone] in words. */
data class LessonTeacher(val name: String, val isu: Int?, val tone: UiText)

/** A sport queue or booking: a row that leads to Спорт. */
data class LessonSport(val title: UiText, val text: UiText)
