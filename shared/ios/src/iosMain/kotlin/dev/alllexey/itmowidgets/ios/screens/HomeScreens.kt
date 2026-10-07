package dev.alllexey.itmowidgets.ios.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.home.ui.HomeActions
import dev.alllexey.itmowidgets.feature.home.ui.HomeRoute
import dev.alllexey.itmowidgets.feature.home.ui.offeredBy
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.ios.di.IosKoin
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * The home tab's root (`AppRoutes.TabRoot(HOME)`), LH-2's route as Android's shell hosts it, with the renderers of the
 * kinds iOS offers (`offeredBy`: no new-marks card before IO-09d3). Cards and buttons open their keys through [open],
 * the Swift router, as Android's `homeActions` does: a key iOS has no screen for yet opens nothing. The two hints
 * only the platform can act on go to Swift: [showWidgetHowTo] (iOS lets no app place a widget, so the hint explains
 * how) and [requestNotifications] (the system dialog, or the app's notification settings once iOS has asked); the
 * route re-checks the hints when the app becomes active again. The home has no top bar, so the content keeps clear of
 * the status bar, the tab bar and the home indicator on every side.
 */
fun homeViewController(
    open: (AppRoute) -> Unit,
    showWidgetHowTo: () -> Unit,
    requestNotifications: () -> Unit,
): UIViewController {
    val koin = IosKoin.koin()
    val renderers = koin.getAll<HomeCardRenderer>().offeredBy(koin.get<PlatformCapabilities>())
    val actions = homeActions(open, showWidgetHowTo, requestNotifications)
    return screenController {
        Box(
            Modifier
                .fillMaxSize()
                .background(ItmoTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            HomeRoute(actions, renderers = renderers)
        }
    }
}

/**
 * Debug builds only (the `-itmoForgetHomeHints` launch argument): forgets the hints the user closed, so a UI test
 * starts with every hint the device state shows and can close one again.
 */
fun forgetClosedHomeHints() {
    val preferences = IosKoin.koin().get<HomeLayoutPreferences>()
    MainScope().launch { preferences.forgetDismissedHomeHints() }
}

/** Android's `homeActions` (`HomeEntries.kt`) over the same keys; refreshing and dismissing are the route's own. */
private fun homeActions(
    open: (AppRoute) -> Unit,
    showWidgetHowTo: () -> Unit,
    requestNotifications: () -> Unit,
) = HomeActions(
    onLesson = { open(AppRoutes.LessonDetails(it)) },
    onPendingSport = { open(AppRoutes.PendingSportDetails(it)) },
    onOpenSport = { open(AppRoutes.TabRoot(AppTab.SPORT)) },
    onOpenFriends = { open(AppRoutes.Friends) },
    onOpenUser = { isu -> UserScreenArgs.profileIsu(isu.toLong())?.let { open(AppRoutes.UserProfile(it)) } },
    onHint = { hint ->
        when (hint) {
            HomeHint.WIDGETS -> showWidgetHowTo()
            HomeHint.NOTIFICATIONS -> requestNotifications()
            HomeHint.SERVICES -> open(AppRoutes.Settings(SettingsPage.SERVICES.name))
        }
    },
    onOpenScheduleChanges = { open(AppRoutes.ScheduleChanges) },
    onOpenMarks = { open(AppRoutes.TabRoot(AppTab.RECORDBOOK)) },
    onOpenWeb = { open(AppRoutes.MyItmoWeb) },
    onOpenQr = { open(AppRoutes.QrPass) },
)
