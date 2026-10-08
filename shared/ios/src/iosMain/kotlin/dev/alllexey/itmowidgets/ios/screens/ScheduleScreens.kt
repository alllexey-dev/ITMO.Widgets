package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.FriendSelectionContract
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorIosRoute
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.ui.LessonDetailsIosRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.PendingSportDetailsIosRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleSheetExits
import dev.alllexey.itmowidgets.feature.schedule.ui.UserScheduleIosRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.ScheduleChangesRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRoute
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteActions
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleRouteRequest
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import platform.UIKit.UIViewController

/**
 * The schedule tab's root (`AppRoutes.TabRoot(SCHEDULE)`): L10's `ScheduleRoute` of the own schedule with the
 * [controller] Swift hosts and the requests `ScheduleFragment` takes as Fragment results. [showToday] is the `today`
 * entry route (the own schedule on today); [showFriend] is the friend picker's answer. Requests sent before the
 * first frame wait in an unlimited buffer, as on Android.
 */
class ScheduleRootScreen internal constructor(
    val controller: UIViewController,
    private val requests: Channel<ScheduleRouteRequest>,
) {
    fun showToday() {
        requests.trySend(ScheduleRouteRequest.Today)
    }

    /** [user]'s schedule, or the own one for null. */
    fun showFriend(user: UserSummary?) {
        val selected = user?.let { SelectedUser(isu = it.isu, name = it.name, avatar = it.pictureUrl) }
        requests.trySend(ScheduleRouteRequest.SelectUser(selected))
    }
}

/**
 * Builds the schedule root. A lesson opens its sheet (`AppRoutes.LessonDetails`) and a queued sport row the
 * schedule's pending sheet through [open], the Swift router; the friends button asks Swift for the picker with the
 * ISU shown now ([pickFriend], `FriendSelectionContract.NO_USER_ISU` for the own schedule). The list scrolls under
 * the tab bar, so the content keeps clear of the bars on every side: the route has no content padding parameter.
 */
fun scheduleRootScreen(open: (AppRoute) -> Unit, pickFriend: (Int) -> Unit): ScheduleRootScreen {
    val zone = IosKoin.koin().get<AcademicTimeProvider>().timeZone
    val requests = Channel<ScheduleRouteRequest>(Channel.UNLIMITED)
    val flow = requests.receiveAsFlow()
    val actions = ScheduleRouteActions(
        onLessonClick = { lesson, date -> open(AppRoutes.LessonDetails(lesson.toDetailsArgs(date))) },
        onPendingClick = { booking -> open(AppRoutes.PendingSportDetails(booking.toDetailsArgs(zone))) },
        onPickFriend = { shown -> pickFriend(shown?.isu ?: FriendSelectionContract.NO_USER_ISU) },
    )
    val controller = screenController {
        Padded { ScheduleRoute(ownTab = true, actions = actions, requests = flow) }
    }
    return ScheduleRootScreen(controller, requests)
}

/**
 * Another user's schedule (`AppRoutes.UserSchedule`), as `UserScheduleFragment` hosts it: every lesson opens the
 * lesson sheet through [open]; no pending rows, no friend picker, no `today` request.
 */
fun userScheduleViewController(
    isu: Int,
    name: String,
    open: (AppRoute) -> Unit,
    onBack: () -> Unit,
): UIViewController = screenController {
    val actions = ScheduleRouteActions(
        onLessonClick = { lesson, date -> open(AppRoutes.LessonDetails(lesson.toDetailsArgs(date))) },
    )
    Padded { UserScheduleIosRoute(isu, name, actions, onBack) }
}

/** The found schedule changes (`AppRoutes.ScheduleChanges`), as `ScheduleChangesFragment` hosts them. */
fun scheduleChangesViewController(onBack: () -> Unit): UIViewController = screenController {
    Padded { ScheduleChangesRoute(onBack = onBack) }
}

/**
 * The lesson sheet's content (`AppRoutes.LessonDetails`) inside a SwiftUI sheet: the teacher's or a friend's profile
 * opens through [open], which closes the sheet first; the map and the meeting link go to the system, and when nothing
 * takes them Swift says so ([mapUnavailable], [linkFailed]).
 */
fun lessonDetailsViewController(
    lesson: LessonDetailsArgs,
    open: (AppRoute) -> Unit,
    onClose: () -> Unit,
    mapUnavailable: () -> Unit,
    linkFailed: () -> Unit,
): UIViewController {
    val exits = ScheduleSheetExits(
        onProfile = { isu -> open(AppRoutes.UserProfile(isu)) },
        onClose = onClose,
        onMapUnavailable = mapUnavailable,
        onLinkFailed = linkFailed,
    )
    return sheetController { LessonDetailsIosRoute(lesson, exits) }
}

/**
 * The schedule's sheet of a queued or predicted sport booking (`AppRoutes.PendingSportDetails`): the teacher's
 * profile and the sport tab open through [open] after [onClose].
 */
fun pendingSportDetailsViewController(
    booking: PendingSportDetailsArgs,
    open: (AppRoute) -> Unit,
    onClose: () -> Unit,
    mapUnavailable: () -> Unit,
): UIViewController {
    val exits = ScheduleSheetExits(
        onProfile = { isu -> open(AppRoutes.UserProfile(isu)) },
        onClose = onClose,
        onMapUnavailable = mapUnavailable,
        onLinkFailed = {},
        onOpenSport = {
            onClose()
            open(AppRoutes.TabRoot(AppTab.SPORT))
        },
    )
    return sheetController { PendingSportDetailsIosRoute(booking, exits) }
}

/**
 * The schedule's friend picker (`AppRoutes.FriendSelector`) inside a SwiftUI sheet: the choice goes to [deliver]
 * once (the person, or null for the own schedule) and Swift closes the sheet; a profile opens through [open] in the
 * sheet's place.
 */
fun friendSelectorViewController(
    selectedIsu: Int,
    deliver: (UserSummary?) -> Unit,
    open: (AppRoute) -> Unit,
    onClose: () -> Unit,
): UIViewController = sheetController {
    FriendSelectorIosRoute(
        selectedIsu = selectedIsu,
        onDeliver = deliver,
        onOpenProfile = { user -> open(AppRoutes.UserProfile(user.isu)) },
        onClose = onClose,
    )
}

/** The routes draw their own top bar and no insets (DS-03), so the host pads the safe area on every side. */
@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        content()
    }
}

/**
 * A sheet's content in a see-through controller ([sheetController]): the SwiftUI sheet's grouped background shows
 * around the `SheetScaffold`, which draws the same colour. Only the sides and the home indicator are kept clear: the
 * sheet starts below the status bar, whose height Compose still reports.
 */
private fun sheetController(content: @Composable () -> Unit): UIViewController = screenController(opaque = false) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        content()
    }
}
