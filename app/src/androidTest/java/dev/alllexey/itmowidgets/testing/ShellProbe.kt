package dev.alllexey.itmowidgets.testing

import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.app.shell.ShellHost
import dev.alllexey.itmowidgets.app.shell.TabPages
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.RouteKind
import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.navigation.from

/**
 * What `MainActivity` shows, in the shell's own values (SH-1a2): the [surface], the selected [tab] (null off the
 * tabs), the [overlays] bottom to top and the top sheet or dialog ([floating]). [swipeEnabled]: a horizontal swipe on
 * the tab content switches the tab now (SH-SW1); never in the legacy shell, which has no swipe.
 */
data class ShellView(
    val surface: ShellSurface,
    val tab: AppTab?,
    val overlays: List<AppRoute>,
    val floating: AppRoute?,
    val swipeEnabled: Boolean = false,
) {
    /** The key class name of [floating], the same in both shells ([ShellProbe.LegacyKey.name] in the legacy one). */
    val floatingName: String?
        get() = (floating as? ShellProbe.LegacyKey)?.name ?: floating?.let { it::class.simpleName }
}

/**
 * Reads [ShellView] from `MainActivity` in either shell, so one test body asserts both: the Navigation 3 shell through
 * `ShellHost`'s read-only snapshot, the legacy one from the Fragment tree as the tests read it before.
 *
 * The legacy reading maps the root and overlay destinations to their keys with their arguments. A sheet or a dialog
 * comes back as a [LegacyKey] named like its key class, since the Fragment's arguments are not the key's fields; the
 * shell's own `MaterialAlertDialogBuilder` alerts are not Fragments and do not show at all. An overlay without a core
 * key (`app_update`) is a [LegacyKey] too.
 */
object ShellProbe {

    /** A legacy destination, sheet or dialog that the probe names but cannot rebuild as a key. */
    data class LegacyKey(val name: String, override val kind: RouteKind) : AppRoute

    /** The view of the one resumed `MainActivity`, read on the main thread. */
    fun current(): ShellView {
        var view: ShellView? = null
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                view = read(activity)
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
        return checkNotNull(view)
    }

    /** Call on the main thread. */
    fun read(activity: MainActivity): ShellView =
        ShellHost.of(activity)?.let { nav3(activity, it) } ?: legacy(activity)

    private fun nav3(activity: MainActivity, host: ShellHost): ShellView {
        val (surface, stack) = host.snapshot
        val tabs = surface is ShellSurface.Tabs
        val exploring = activity.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        return ShellView(
            surface = surface,
            tab = stack.tab.takeIf { tabs },
            overlays = if (tabs) stack.overlays else emptyList(),
            floating = if (tabs) stack.floating.lastOrNull() else null,
            swipeEnabled = TabPages.swipeEnabled(surface, stack, exploring),
        )
    }

    private fun legacy(activity: MainActivity): ShellView {
        val fragments = activity.supportFragmentManager
        val root = fragments.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val controller = root.navController
        val surface = when {
            activity.findViewById<View>(R.id.session_progress).visibility == View.VISIBLE -> ShellSurface.Progress
            controller.currentDestination?.id == R.id.auth -> ShellSurface.Auth
            controller.currentDestination?.id == R.id.onboarding -> ShellSurface.Onboarding
            else -> ShellSurface.Tabs(demoBanner = activity.findViewById<View>(R.id.demo_banner).visibility == View.VISIBLE)
        }
        if (surface !is ShellSurface.Tabs) return ShellView(surface, null, emptyList(), null)
        val tab = controller.currentBackStack.value.lastOrNull { it.destination.id in TABS }
            ?.let { TABS.getValue(it.destination.id) }
        val overlayHost = (fragments.findFragmentByTag(OVERLAY) as? NavHostFragment)?.takeUnless { it.isRemoving }
        val overlays = overlayHost?.navController?.currentBackStack?.value.orEmpty()
            .filterNot { it.destination is NavGraph }
            .map(::overlayKey)
        val floating = shownDialogs(fragments).lastOrNull()?.let { dialog -> LegacyKey(dialog.keyName, dialog.kind) }
        return ShellView(surface, tab, overlays, floating)
    }

    private fun overlayKey(entry: NavBackStackEntry): AppRoute {
        val args = entry.arguments
        val isu = args?.getInt(UserScreenArgs.ISU) ?: 0
        val name = args?.getString(UserScreenArgs.NAME).orEmpty()
        return when (entry.destination.id) {
            R.id.settings -> AppRoutes.Settings(args?.getString(SettingsScreenArgs.PAGE) ?: AppRoutes.Settings.ROOT_PAGE)
            R.id.diagnostics -> AppRoutes.Diagnostics
            R.id.debug_tools -> AppRoutes.DebugTools
            R.id.recordbook_subject -> RecordbookSubjectArgs.from(args)?.let(AppRoutes::RecordbookSubject)
            R.id.friends -> AppRoutes.Friends
            R.id.user_friends -> AppRoutes.UserFriends(isu, name)
            R.id.user_search -> AppRoutes.UserSearch
            R.id.user_profile -> AppRoutes.UserProfile(isu)
            R.id.user_schedule -> AppRoutes.UserSchedule(isu, name)
            R.id.user_sport -> AppRoutes.UserSport(isu, name)
            R.id.schedule_changes -> AppRoutes.ScheduleChanges
            R.id.qr_pass -> AppRoutes.QrPass
            R.id.my_itmo_web -> AppRoutes.MyItmoWeb
            else -> null
        } ?: LegacyKey(entry.destination.label?.toString() ?: entry.destination.id.toString(), RouteKind.SCREEN)
    }

    /** Every shown sheet or dialog Fragment, parents before their children. */
    private fun shownDialogs(fragments: FragmentManager): List<DialogFragment> = fragments.fragments.flatMap { fragment ->
        val own = (fragment as? DialogFragment)?.takeIf { it.dialog?.isShowing == true }
        listOfNotNull(own) + if (fragment.isAdded) shownDialogs(fragment.childFragmentManager) else emptyList()
    }

    /** `SubjectLinksBottomSheet` -> `SubjectLinks`, `ReportReviewDialogFragment` -> `ReportReview`: the key's name. */
    private val Fragment.keyName: String
        get() = javaClass.simpleName.removeSuffix("BottomSheet").removeSuffix("DialogFragment")

    private val DialogFragment.kind: RouteKind
        get() = if (this is BottomSheetDialogFragment) RouteKind.SHEET else RouteKind.DIALOG

    private const val OVERLAY = "app-overlay"

    private val TABS = mapOf(
        R.id.navigation_recordbook to AppTab.RECORDBOOK,
        R.id.navigation_schedule to AppTab.SCHEDULE,
        R.id.navigation_home to AppTab.HOME,
        R.id.navigation_sport to AppTab.SPORT,
        R.id.navigation_me to AppTab.ME,
    )
}
