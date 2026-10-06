package dev.alllexey.itmowidgets.feature.home.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.SettingsScreenArgs
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.ui.navigation.AppRoot
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openLessonDetails
import dev.alllexey.itmowidgets.core.ui.navigation.openPendingSportDetails
import dev.alllexey.itmowidgets.core.ui.navigation.openRoot
import dev.alllexey.itmowidgets.core.ui.navigation.openScreen
import dev.alllexey.itmowidgets.core.ui.navigation.openUserProfile
import dev.alllexey.itmowidgets.core.ui.permission.RequestNotificationPermission
import dev.alllexey.itmowidgets.core.ui.permission.openNotificationSettingsIfLocked
import dev.alllexey.itmowidgets.core.ui.permission.requestNotifications
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.designsystem.host.itmoComposeView
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The home tab (`navigation_home`), kept by name for the main graph. The feed is `HomeRoute` from
 * `:shared:feature-home`; this host keeps what only Android does: navigation through `AppNavigator` and the hint
 * actions of [HomeHints]. Its ViewModel is the route's: Koin's, in this Fragment's store, never Hilt's default
 * factory.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModel()
    private var hints: HomeHints? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(RequestNotificationPermission()) { granted ->
            requireActivity().onHomeNotificationResult(granted, viewModel)
        }

    private val actions = HomeActions(
        onLesson = { openLessonDetails(it) },
        onPendingSport = { openPendingSportDetails(it) },
        onOpenSport = { openRoot(AppRoot.SPORT) },
        onOpenFriends = { openScreen(AppScreen.FRIENDS) },
        onOpenUser = { openUserProfile(it) },
        onHint = { hint ->
            hints?.act(hint) { page -> openScreen(AppScreen.SETTINGS, bundleOf(SettingsScreenArgs.PAGE to page)) }
        },
        onOpenScheduleChanges = { openScreen(AppScreen.SCHEDULE_CHANGES) },
        onOpenMarks = { openRoot(AppRoot.RECORDBOOK) },
        onOpenWeb = { openScreen(AppScreen.MY_ITMO_WEB) },
        onOpenQr = { openScreen(AppScreen.QR_PASS) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launcher confirms a pin with this screen stopped; the hint source re-checks on return.
        hints = HomeHints(requireContext(), notificationPermissionLauncher)
            .also { it.start(viewModel::onScreenResumed) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView { HomeRoute(actions, viewModel) }

    override fun onDestroy() {
        hints?.stop()
        hints = null
        super.onDestroy()
    }
}

/**
 * The platform side of the home hints, the same in `HomeFragment` and the Compose shell's home tab: the widget hint
 * asks the launcher to pin the single-lesson widget, the notifications hint asks for the permission through
 * [notifications] (or opens the system page when nothing is left to ask), the services hint opens the services page
 * of the settings. The host owns the instance: [start] when it is created, [stop] when it goes, since the launcher
 * confirms a pin while the screen is stopped.
 */
internal class HomeHints(
    private val context: Context,
    private val notifications: ActivityResultLauncher<Unit>,
) {
    private val pins = WidgetPinRequester(context)

    /** [onPinned] runs for every widget the launcher pinned. */
    fun start(onPinned: () -> Unit) {
        pins.start { onPinned() }
    }

    fun stop() {
        pins.stop()
    }

    /** Runs [hint]'s action; [openSettings] opens a page of the settings, named as `SettingsScreenArgs.PAGE`. */
    fun act(hint: HomeHint, openSettings: (page: String) -> Unit) {
        when (hint) {
            HomeHint.WIDGETS -> pins.request(WidgetProviders.SINGLE_LESSON)
            HomeHint.NOTIFICATIONS -> context.requestNotifications(notifications)
            HomeHint.SERVICES -> openSettings(SERVICES_SETTINGS_PAGE)
        }
    }

    companion object {
        const val SERVICES_SETTINGS_PAGE = "SERVICES"
    }
}

/** The permission dialog closed: a locked denial opens the system page, and the hint source re-checks. */
internal fun Activity.onHomeNotificationResult(granted: Boolean, viewModel: HomeViewModel) {
    openNotificationSettingsIfLocked(granted)
    viewModel.onScreenResumed()
}
