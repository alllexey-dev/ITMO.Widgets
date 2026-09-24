package dev.alllexey.itmowidgets.core.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs

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

    /** Sheets sit above whatever is open; they belong to no back stack and take no history. */
    fun openLessonDetails(args: LessonDetailsArgs)

    fun openPendingSportDetails(args: PendingSportDetailsArgs)

    /** All links of a subject period. */
    fun openSubjectLinks(args: SubjectLinksArgs)

    /** Adds a link, or edits the viewer's own link [linkId]. */
    fun openLinkEditor(args: SubjectLinksArgs, linkId: String? = null)

    fun openLinkActions(args: SubjectLinksArgs, linkId: String)

    /** Approves a browser's sign-in to the web version. */
    fun openWebLogin()
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

fun Fragment.openLessonDetails(args: LessonDetailsArgs) {
    (requireActivity() as AppNavigator).openLessonDetails(args)
}

fun Fragment.openPendingSportDetails(args: PendingSportDetailsArgs) {
    (requireActivity() as AppNavigator).openPendingSportDetails(args)
}

fun Fragment.closeScreen() {
    val controller = findNavController()
    if (controller.previousBackStackEntry != null) controller.navigateUp()
    else requireActivity().onBackPressedDispatcher.onBackPressed()
}

fun Fragment.openSubjectLinks(args: SubjectLinksArgs) {
    (requireActivity() as AppNavigator).openSubjectLinks(args)
}

fun Fragment.openLinkEditor(args: SubjectLinksArgs, linkId: String? = null) {
    (requireActivity() as AppNavigator).openLinkEditor(args, linkId)
}

fun Fragment.openLinkActions(args: SubjectLinksArgs, linkId: String) {
    (requireActivity() as AppNavigator).openLinkActions(args, linkId)
}

fun Fragment.openWebLogin() {
    (requireActivity() as AppNavigator).openWebLogin()
}
