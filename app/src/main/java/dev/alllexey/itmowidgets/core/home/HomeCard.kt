package dev.alllexey.itmowidgets.core.home

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import java.time.LocalDate

/** Feed order is the declaration order; a kind is also the unit the user can hide. */
enum class HomeCardKind {
    SCHEDULE, SPORT, FRIEND_REQUESTS, HINT_WIDGETS, HINT_NOTIFICATIONS, HINT_SERVICES
}

/** A dismissible nudge; each one has its own card kind so it sorts and hides on its own. */
enum class HomeHint(val kind: HomeCardKind) {
    WIDGETS(HomeCardKind.HINT_WIDGETS),
    NOTIFICATIONS(HomeCardKind.HINT_NOTIFICATIONS),
    SERVICES(HomeCardKind.HINT_SERVICES)
}

enum class HomeLessonState { CURRENT, NEXT, UPCOMING }

sealed interface HomeScheduleRow {
    /** [progress] is the elapsed share of a [HomeLessonState.CURRENT] lesson, 0..1; `null` otherwise. */
    data class Lesson(val args: LessonDetailsArgs, val state: HomeLessonState, val progress: Float? = null) : HomeScheduleRow

    data class PendingSport(val args: PendingSportDetailsArgs, val predicted: Boolean) : HomeScheduleRow
}

/** One card of the home feed; every field is ready to render, sources do the selection. */
sealed interface HomeCard {
    val kind: HomeCardKind

    /** [rows] are what is still ahead; [completed] counts today's lessons already over. */
    data class Schedule(
        val date: LocalDate,
        val tomorrow: Boolean,
        val rows: List<HomeScheduleRow>,
        val completed: Int
    ) : HomeCard {
        override val kind: HomeCardKind get() = HomeCardKind.SCHEDULE
    }

    data class Sport(val score: SportScoreSummary?, val queue: List<PendingSportBooking>) : HomeCard {
        override val kind: HomeCardKind get() = HomeCardKind.SPORT
    }

    data class FriendRequests(val incoming: List<UserSummary>) : HomeCard {
        override val kind: HomeCardKind get() = HomeCardKind.FRIEND_REQUESTS
    }

    data class Hint(val hint: HomeHint) : HomeCard {
        override val kind: HomeCardKind get() = hint.kind
    }
}
