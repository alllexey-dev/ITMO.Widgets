package dev.alllexey.itmowidgets.feature.home.ui

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.navigation.WidgetProviders
import dev.alllexey.itmowidgets.core.ui.permission.openNotificationSettingsIfLocked
import dev.alllexey.itmowidgets.core.ui.permission.requestNotifications
import dev.alllexey.itmowidgets.core.ui.widget.WidgetPinRequester
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel

/**
 * The platform side of the home hints, owned by the Compose shell's home tab: the widget hint
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
