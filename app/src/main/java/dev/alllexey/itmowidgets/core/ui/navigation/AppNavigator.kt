package dev.alllexey.itmowidgets.core.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

enum class AppScreen {
    SETTINGS, DEBUG_TOOLS, RECORDBOOK_SUBJECT, APP_UPDATE,
    FRIENDS, USER_SEARCH, USER_PROFILE, USER_SCHEDULE, USER_SPORT
}

/** Contextual screens belong to the transient overlay stack, never to a bottom tab's history. */
interface AppNavigator {
    fun openScreen(screen: AppScreen, arguments: Bundle? = null)
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

fun Fragment.closeScreen() {
    val controller = findNavController()
    if (controller.previousBackStackEntry != null) controller.navigateUp()
    else requireActivity().onBackPressedDispatcher.onBackPressed()
}
