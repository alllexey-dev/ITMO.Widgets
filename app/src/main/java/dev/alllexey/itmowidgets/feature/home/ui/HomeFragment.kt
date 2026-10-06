package dev.alllexey.itmowidgets.feature.home.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * `:shared:feature-home`; this host keeps what only Android does: navigation through `AppNavigator`, the widget pin,
 * the notification permission and the services settings page. Its ViewModel is the route's: Koin's, in this
 * Fragment's store, never Hilt's default factory.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModel()
    private var pinRequester: WidgetPinRequester? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(RequestNotificationPermission()) { granted ->
            requireActivity().openNotificationSettingsIfLocked(granted)
            viewModel.onScreenResumed()
        }

    private val actions = HomeActions(
        onLesson = { openLessonDetails(it) },
        onPendingSport = { openPendingSportDetails(it) },
        onOpenSport = { openRoot(AppRoot.SPORT) },
        onOpenFriends = { openScreen(AppScreen.FRIENDS) },
        onOpenUser = { openUserProfile(it) },
        onHint = { actOnHint(it) },
        onOpenScheduleChanges = { openScreen(AppScreen.SCHEDULE_CHANGES) },
        onOpenMarks = { openRoot(AppRoot.RECORDBOOK) },
        onOpenWeb = { openScreen(AppScreen.MY_ITMO_WEB) },
        onOpenQr = { openScreen(AppScreen.QR_PASS) },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The launcher confirms a pin with this screen stopped; the hint source re-checks on return.
        pinRequester = WidgetPinRequester(requireContext()).also { it.start { viewModel.onScreenResumed() } }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        itmoComposeView { HomeRoute(actions, viewModel) }

    override fun onDestroy() {
        pinRequester?.stop()
        pinRequester = null
        super.onDestroy()
    }

    private fun actOnHint(hint: HomeHint) {
        when (hint) {
            HomeHint.WIDGETS -> pinRequester?.request(WidgetProviders.SINGLE_LESSON)
            HomeHint.NOTIFICATIONS -> requireContext().requestNotifications(notificationPermissionLauncher)
            HomeHint.SERVICES -> openScreen(AppScreen.SETTINGS, bundleOf(SettingsScreenArgs.PAGE to SERVICES_PAGE))
        }
    }

    private companion object {
        const val SERVICES_PAGE = "SERVICES"
    }
}
