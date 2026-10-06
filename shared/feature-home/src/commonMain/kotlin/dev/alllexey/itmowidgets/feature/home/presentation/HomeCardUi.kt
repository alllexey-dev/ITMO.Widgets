package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * One card of the feed as the screen draws it: times and dates are formatted in the academic time zone and every
 * text is a [UiText] or plain data, so the UI parses nothing. [HomeCardFormatter] builds them from `HomeCard`s.
 */
sealed interface HomeCardUi {
    val kind: HomeCardKind

    /** [title] is `Сегодня` or `Завтра`; [footer] counts finished lessons or says the day is empty or over. */
    data class Schedule(
        val title: UiText,
        val date: String,
        val rows: List<HomeScheduleRowUi>,
        val footer: UiText?,
    ) : HomeCardUi {
        override val kind: HomeCardKind get() = HomeCardKind.SCHEDULE
    }

    data class ScheduleChanges(val unread: Int, val latest: UiText) : HomeCardUi {
        override val kind: HomeCardKind get() = HomeCardKind.SCHEDULE_CHANGES
    }

    /** [count] unread subjects, named in [subjects]. */
    data class Marks(val count: Int, val subjects: UiText) : HomeCardUi {
        override val kind: HomeCardKind get() = HomeCardKind.MARKS
    }

    /** [queue] holds the first rows of the own queues and [more] the number left out. */
    data class Sport(val score: HomeSportScoreUi?, val queue: List<HomeSportRowUi>, val more: Int) : HomeCardUi {
        override val kind: HomeCardKind get() = HomeCardKind.SPORT
    }

    /** [incoming] holds the first requests, [count] all of them. */
    data class FriendRequests(val count: Int, val incoming: List<HomeFriendUi>) : HomeCardUi {
        override val kind: HomeCardKind get() = HomeCardKind.FRIEND_REQUESTS
    }

    data class Hint(val hint: HomeHint) : HomeCardUi {
        override val kind: HomeCardKind get() = hint.kind
    }
}

/** What a schedule row opens. */
sealed interface HomeRowTarget {
    data class Lesson(val args: LessonDetailsArgs) : HomeRowTarget

    data class PendingSport(val args: PendingSportDetailsArgs) : HomeRowTarget
}

/**
 * A lesson or a pending sport lesson of the schedule card: `HH:mm` times, the lesson type's colour by [typeId], a
 * [badge] (`сейчас`, `далее`, `ждём запись`, `прогноз`) and, for the lesson in progress, its elapsed share in
 * [progress]; a row with a [progress] is the focused one.
 */
data class HomeScheduleRowUi(
    val start: String,
    val end: String,
    val title: String,
    val subtitle: UiText,
    val typeId: Int,
    val badge: UiText?,
    val progress: Float?,
    val target: HomeRowTarget,
)

/** The sport score out of 100 and the points still missing. */
data class HomeSportScoreUi(val total: Int, val remaining: Int)

/** An own queue entry: the section, `вт, 1 сент.`, the start and the end and `ждём запись` or `прогноз`. */
data class HomeSportRowUi(
    val title: String,
    val subtitle: String,
    val badge: UiText,
    val args: PendingSportDetailsArgs,
)

/** An incoming friend request; [group] is the primary group's name. */
data class HomeFriendUi(val isu: Int, val name: String, val pictureUrl: String?, val group: String?)
