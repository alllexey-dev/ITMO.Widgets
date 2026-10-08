package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import dev.alllexey.itmowidgets.feature.home.ui.HomeActions
import dev.alllexey.itmowidgets.feature.home.ui.HomeHints
import dev.alllexey.itmowidgets.feature.home.ui.HomeRoute
import dev.alllexey.itmowidgets.feature.home.ui.onHomeNotificationResult
import dev.alllexey.itmowidgets.feature.qr.ui.QrPassRoute

/** The home tab's overlays: the QR pass (route map row 20), opened by the home button, the tile and the shortcut. */
internal fun EntryRegistry.Builder.homeEntries() {
    entry<AppRoutes.QrPass> { key, navigator -> QrPassRoute(onBack = { navigator.close(key) }) }
}

/**
 * The home tab's root (route map row 5): `HomeRoute` with its ViewModel from Koin's definition, kept in the root's own
 * store, so the hint callbacks reach the feed's instance. Cards open their sheets, overlays and tabs through
 * [navigator]; the hint actions are [HomeHints], owned by this entry's composition as `HomeFragment` owns them from
 * create to destroy.
 */
@Composable
internal fun HomeTabRoot(navigator: Nav3AppNavigator) {
    val context = LocalContext.current
    val viewModel: HomeViewModel = viewModel { KoinStarter.ensureStarted(context).get() }
    val activity = LocalActivity.current
    val notifications = rememberLauncherForActivityResult(RequestNotificationPermission()) { granted ->
        activity?.onHomeNotificationResult(granted, viewModel)
    }
    val hints = remember(context, notifications) { HomeHints(context, notifications) }
    val sport = rememberSportDetailsRedirect()
    DisposableEffect(hints, viewModel) {
        hints.start(viewModel::onScreenResumed)
        onDispose { hints.stop() }
    }
    val actions = remember(navigator, hints, sport) { homeActions(navigator, hints, sport) }
    HomeRoute(actions, viewModel)
}

/**
 * The routes of the home cards (route map rows 3, 6, 8, 13, 16, 19-21, S11, S12); a sport row goes through [sport],
 * which opens the sport tab's sheet when it finds the booking.
 */
private fun homeActions(navigator: Nav3AppNavigator, hints: HomeHints, sport: SportDetailsRedirect) = HomeActions(
    onLesson = { sport.openLesson(navigator, it) },
    onPendingSport = { sport.openPendingSport(navigator, it) },
    onOpenSport = { navigator.select(AppTab.SPORT) },
    onOpenFriends = { navigator.open(AppRoutes.Friends) },
    onOpenUser = { isu -> UserScreenArgs.profileIsu(isu.toLong())?.let { navigator.open(AppRoutes.UserProfile(it)) } },
    onHint = { hint -> hints.act(hint) { page -> navigator.open(AppRoutes.Settings(page)) } },
    onOpenScheduleChanges = { navigator.open(AppRoutes.ScheduleChanges) },
    onOpenMarks = { navigator.select(AppTab.RECORDBOOK) },
    onOpenWeb = { navigator.open(AppRoutes.MyItmoWeb) },
    onOpenQr = { navigator.open(AppRoutes.QrPass) },
)
