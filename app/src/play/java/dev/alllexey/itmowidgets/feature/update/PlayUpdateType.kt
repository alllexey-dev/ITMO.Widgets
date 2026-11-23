package dev.alllexey.itmowidgets.feature.update

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * The in-app update Google Play can start for this build right now: flexible for a supported build, immediate for
 * an unsupported one; null when Play serves this device no update of that type. The offer (`PlayUpdateAvailability`)
 * and its button (`PlayUpdateAction`) both ask here, so the screen opens only when the button has something to
 * start.
 */
@AppUpdateType
internal fun AppUpdateInfo.startableUpdateType(unsupported: Boolean): Int? {
    val type = if (unsupported) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
    val available = when (updateAvailability()) {
        UpdateAvailability.UPDATE_AVAILABLE -> true
        // An immediate update left halfway resumes where it stopped. A flexible one in progress needs no offer:
        // it is downloading, or InstallStateWatcher already offers the restart.
        UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> type == AppUpdateType.IMMEDIATE
        else -> false
    }
    return type.takeIf { available && isUpdateTypeAllowed(type) }
}
