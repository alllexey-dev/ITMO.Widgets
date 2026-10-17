package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportKindTone
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportPrompt
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * `Запись` of the Спорт tab (docs/features/sport.md): the [query], the quick [chips], the [weeks] strip with its
 * [selectedWeek], the chosen day's lessons in [body], the more-filters sheet ([filters], null while closed) with
 * [filterCount] on its button, and the queue's question ([prompt]).
 */
data class SportSignUiState(
    val query: String = "",
    val chips: List<SportChip> = emptyList(),
    val filterCount: Int = 0,
    val weeks: List<List<SportWeekDay>> = emptyList(),
    val selectedWeek: Int = 0,
    val body: SportSignBody = SportSignBody.Loading,
    val filters: SportFiltersUi? = null,
    val prompt: SportPrompt? = null,
)

/** What `Запись` asks of its screen once. */
sealed interface SportSignEvent {
    /** A message for a snackbar: `Записали`, an error. */
    data class Notice(val text: UiText) : SportSignEvent

    /** A shared link found its lesson: its sheet opens as if the row had been tapped. */
    data class OpenLesson(val lessonId: Long) : SportSignEvent
}

/** A quick filter chip: `Есть места`, `Утро`, `Вечер`, `Друзья`, in that order. */
data class SportChip(val filter: SportQuickFilter, val label: UiText, val selected: Boolean)

enum class SportQuickFilter { FREE, MORNING, EVENING, FRIENDS }

/** A day of the strip: `пн` over `7`, a dot per visible lesson (at most five). */
data class SportWeekDay(
    val date: LocalDate,
    val weekday: String,
    val number: String,
    val lessons: Int,
    val today: Boolean,
    val past: Boolean,
    val selected: Boolean,
    /** What VoiceOver and TalkBack read: `вторник, 8 сентября, 8 занятий`. */
    val description: UiText,
)

sealed interface SportSignBody {
    /** The catalog has not answered yet. */
    data object Loading : SportSignBody

    /** The catalog failed and nothing was loaded before: `Не удалось загрузить` with `Повторить`. */
    data class Failed(val error: AppError) : SportSignBody

    /**
     * The chosen day: its [title] (`Вторник, 8 сентября`), the [count] (`8 занятий`, `2 из 8`), the [rows], the
     * [hidden] line when filters hide some, or [empty] when nothing is shown. [refreshing] while a pull runs;
     * [partialError] when a source behind the rows failed.
     */
    data class Day(
        val title: UiText,
        val count: UiText?,
        val rows: List<SportCatalogRow>,
        val hidden: UiText? = null,
        val empty: SportDayEmpty? = null,
        val refreshing: Boolean = false,
        val partialError: Boolean = false,
    ) : SportSignBody
}

/** Nothing to show on the day: why ([title], [text]) and the way out ([action]). */
data class SportDayEmpty(val title: UiText, val text: UiText?, val action: SportEmptyAction?, val filtered: Boolean)

sealed interface SportEmptyAction {
    val label: UiText

    /** `Показать понедельник`: the next day with lessons. */
    data class ShowDay(val date: LocalDate, override val label: UiText) : SportEmptyAction

    /** `Сбросить фильтры`. */
    data class ResetFilters(override val label: UiText) : SportEmptyAction
}

/**
 * One catalog lesson as a dense row: [start] over [end], the [section], its [kind] and short [place] (`Свободное,
 * Кронва`), the [occupancy] (`22 из 24`), the friends who go ([friends], names and photos), a [clash] with a lesson
 * (`Пара: Алгоритмы`) and one [action].
 */
data class SportCatalogRow(
    val lessonId: Long,
    val start: String,
    val end: String,
    val section: String,
    val kind: UiText,
    val kindTone: SportKindTone,
    val place: UiText?,
    val occupancy: SportOccupancyUi?,
    val friends: List<UserSummary>,
    val clash: UiText?,
    val action: SportRowAction,
)

/** `22 из 24` over a short bar; [tone] warns when almost full and turns red when full. */
data class SportOccupancyUi(val taken: Int, val total: Int, val text: UiText, val tone: SportSeatsTone) {
    val share: Float get() = if (total > 0) (taken.toFloat() / total).coerceIn(0f, 1f) else 1f
}

enum class SportSeatsTone { OK, FEW, FULL }

/** The one action of a row. */
sealed interface SportRowAction {
    /** `Записаться`; [busy] while the request runs. */
    data class Sign(val busy: Boolean) : SportRowAction

    /** `В очередь`. */
    data class Queue(val busy: Boolean) : SportRowAction

    /** Already there: `записаны` or `очередь 2 из 5`. */
    data class Status(val text: UiText, val booked: Boolean) : SportRowAction

    /** Closed for the viewer, with MyITMO's reason (`Только для сборной`). */
    data class Blocked(val reason: UiText) : SportRowAction
}

/**
 * The more-filters sheet: the [sections] to pick, the [buildings] (`Все` first, then the dictionary's, `Другие корпуса`
 * and `Онлайн`), the part of the day ([dayPart], shown when [showDayPart]), the [teachers] (null when the setting hides
 * the row), the switches and the number of lessons the filters leave ([shownCount]).
 *
 * [sections] are the own and the most popular types with the selected ones and [moreSections] (`Ещё 23`) after them;
 * after `Ещё` they are every type matching [sectionQuery], the search over them (null while collapsed).
 */
data class SportFiltersUi(
    val sections: List<SportOption>,
    val moreSections: UiText?,
    val sectionQuery: String?,
    val buildings: List<SportOption>,
    val dayPart: SportDayPart,
    val showDayPart: Boolean,
    val teachers: List<SportOption>?,
    val teacher: String?,
    val onlyFree: Boolean,
    val onlyPinned: Boolean?,
    val onlyFriends: Boolean,
    val shownCount: UiText,
)

/** A choice of the sheet; [value] null is `Все`. */
data class SportOption(val value: String?, val label: UiText, val selected: Boolean)

/** Morning before 12:00, day before 17:00, evening after. */
enum class SportDayPart { ANYTIME, MORNING, DAY, EVENING;

    fun contains(time: LocalTime): Boolean = when (this) {
        ANYTIME -> true
        MORNING -> time < NOON
        DAY -> time >= NOON && time < EVENING_START
        EVENING -> time >= EVENING_START
    }

    private companion object {
        val NOON = LocalTime(12, 0)
        val EVENING_START = LocalTime(17, 0)
    }
}

/** The filters of `Запись`; the chosen day travels with them. */
data class SportSignFilters(
    val selectedDate: LocalDate,
    val query: String = "",
    val sections: Set<SectionName> = emptySet(),
    val building: String? = null,
    val teacher: String? = null,
    val dayPart: SportDayPart = SportDayPart.ANYTIME,
    val onlyFree: Boolean = false,
    val onlyPinned: Boolean = false,
    val onlyFriends: Boolean = false,
) {
    /** How many filters narrow the list, for the badge on the filters button; the search does not count. */
    val count: Int
        get() = sections.size + listOfNotNull(building, teacher).size +
            listOf(dayPart != SportDayPart.ANYTIME, onlyFree, onlyPinned, onlyFriends).count { it }

    fun cleared(): SportSignFilters = SportSignFilters(selectedDate = selectedDate, query = query)
}

/** The queue commands a prompt confirms. */
sealed interface SportSignCommand {
    data class CreateFreeSign(val lessonId: Long) : SportSignCommand
    data class CancelFreeSign(val entryId: Long) : SportSignCommand
    data class CreateAutoSign(val prototypeLessonId: Long) : SportSignCommand
    data class CancelAutoSign(val entryId: Long) : SportSignCommand
}

/**
 * The most weeks after the current one the strip may reach. It ends at the week of the catalog's last lesson: My ITMO
 * publishes about two weeks ahead and the queue's predictions add two more.
 */
internal const val MAX_WEEKS_FORWARD = 5

/** The Monday of this date's ISO week. */
internal fun LocalDate.weekMonday(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** Whole weeks from this date's week to [other]'s, negative when [other] is earlier. */
internal fun LocalDate.weeksUntil(other: LocalDate): Int = weekMonday().daysUntil(other.weekMonday()) / 7
