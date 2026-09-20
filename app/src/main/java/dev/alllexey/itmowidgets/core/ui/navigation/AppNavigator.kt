package dev.alllexey.itmowidgets.core.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

enum class AppScreen {
    SETTINGS, DIAGNOSTICS, DEBUG_TOOLS, RECORDBOOK_SUBJECT, APP_UPDATE, QR_PASS, MY_ITMO_WEB,
    FRIENDS, USER_FRIENDS, USER_SEARCH, USER_PROFILE, USER_SCHEDULE, USER_SPORT
}

/** The bottom tabs; selecting one discards the contextual stack. */
enum class AppRoot { RECORDBOOK, SCHEDULE, HOME, SPORT, ME }

/** Contextual screens belong to the transient overlay stack, never to a bottom tab's history. */
interface AppNavigator {
    fun openScreen(screen: AppScreen, arguments: Bundle? = null)

    fun openRoot(root: AppRoot)

    /** Closes the whole contextual stack at once, for a screen that hands the window over. */
    fun dismissOverlays()
}

interface ScreenTransitionHost {
    fun onContentReady()

    companion object {
        const val ARG_OVERLAY_ROOT = "app_overlay_root"
    }
}

fun Fragment.openScreen(screen: AppScreen, arguments: Bundle? = null) {
    (requireActivity() as AppNavigator).openScreen(screen, arguments)
}

fun Fragment.openRoot(root: AppRoot) {
    (requireActivity() as AppNavigator).openRoot(root)
}

fun Fragment.dismissOverlays() {
    (requireActivity() as AppNavigator).dismissOverlays()
}

fun Fragment.closeScreen() {
    val controller = findNavController()
    if (controller.previousBackStackEntry != null) controller.navigateUp()
    else requireActivity().onBackPressedDispatcher.onBackPressed()
}
