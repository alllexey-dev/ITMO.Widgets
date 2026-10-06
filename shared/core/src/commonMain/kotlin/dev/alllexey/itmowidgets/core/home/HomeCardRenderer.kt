package dev.alllexey.itmowidgets.core.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs

/**
 * One feature's drawing of its home cards. The feature that produces a [HomeCard] also draws it: it registers one
 * renderer in its Koin module as a qualified `HomeCardRenderer` (an open set, like `HomeCardSource`), and the feed
 * draws each card with the renderer whose [kinds] hold the card's kind. Every [HomeCardKind] has exactly one renderer.
 */
interface HomeCardRenderer {
    val kinds: Set<HomeCardKind>

    /** Draws [card], one of [kinds], in the feed's margins; [actions] are the host's navigation. */
    @Composable
    fun Content(card: HomeCard, actions: HomeCardActions, modifier: Modifier)
}

/**
 * What a home card can ask the feed's host to do. [onDismiss] is the close button of a card of that kind: a hint is
 * dismissed, schedule changes and new marks are marked read.
 */
data class HomeCardActions(
    val onLesson: (LessonDetailsArgs) -> Unit = {},
    val onPendingSport: (PendingSportDetailsArgs) -> Unit = {},
    val onOpenSport: () -> Unit = {},
    val onOpenFriends: () -> Unit = {},
    val onOpenUser: (isu: Int) -> Unit = {},
    val onOpenScheduleChanges: () -> Unit = {},
    val onOpenMarks: () -> Unit = {},
    val onHint: (HomeHint) -> Unit = {},
    val onDismiss: (HomeCardKind) -> Unit = {},
)

/** Test tags of the rows and buttons inside home cards, the same in every feature that draws one. */
object HomeCardTestTags {
    const val SCHEDULE_ROW = "home_schedule_row"
    const val SPORT_ROW = "home_sport_row"
    const val FRIEND_ROW = "home_friend_row"
    const val DISMISS = "home_card_dismiss"
    const val HINT_ACTION = "home_hint_action"
    const val FRIENDS_ALL = "home_friends_all"
}
