package dev.alllexey.itmowidgets.feature.settings.data

import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundRefreshStatus

/**
 * iOS's side of the background work row: Background App Refresh. Off in Settings or restricted by the device, the
 * system never wakes the app, so the background checks wait for the next launch. Read on the main thread.
 */
class IosBackgroundWorkAccess : BackgroundWorkAccess {

    override fun isUnrestricted(): Boolean =
        UIApplication.sharedApplication.backgroundRefreshStatus ==
            UIBackgroundRefreshStatus.UIBackgroundRefreshStatusAvailable
}
