package dev.alllexey.itmowidgets.feature.update.ui

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dev.alllexey.itmowidgets.BuildConfig
import java.util.concurrent.Executor

/**
 * The Google Play build updates only through Play: a flexible update in the background, an immediate one when
 * the installed build is unsupported. When Play offers no update (not rolled out yet, installed elsewhere) the
 * app's card in Play opens instead.
 *
 * @param callbacks runs the Play task callbacks; the main thread in the app.
 */
class PlayUpdateAction(
    private val manager: AppUpdateManager,
    private val pages: ReleasePageOpener,
    private val callbacks: Executor
) : UpdateAction {

    override fun start(activity: Activity, unsupported: Boolean, onFailed: () -> Unit) {
        manager.appUpdateInfo
            .addOnSuccessListener(callbacks) { info ->
                if (!startFlow(info, activity, unsupported)) openStore(activity, onFailed)
            }
            .addOnFailureListener(callbacks) { openStore(activity, onFailed) }
    }

    private fun startFlow(info: AppUpdateInfo, activity: Activity, unsupported: Boolean): Boolean {
        val type = if (unsupported) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        val available = when (info.updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> true
            // An immediate update left halfway resumes where it stopped.
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> type == AppUpdateType.IMMEDIATE
            else -> false
        }
        if (!available || !info.isUpdateTypeAllowed(type)) return false
        return runCatching {
            manager.startUpdateFlowForResult(info, activity, AppUpdateOptions.defaultOptions(type), REQUEST_CODE)
        }.getOrDefault(false)
    }

    private fun openStore(activity: Activity, onFailed: () -> Unit) {
        val opened = pages.open(activity, "market://details?id=${BuildConfig.APPLICATION_ID}") ||
            pages.open(activity, BuildConfig.DOWNLOAD_URL)
        if (!opened) onFailed()
    }

    private companion object {
        // The result is not read: the flexible download reports through InstallStateWatcher.
        const val REQUEST_CODE = 0x1d
    }
}
