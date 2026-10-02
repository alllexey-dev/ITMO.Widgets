package dev.alllexey.itmowidgets.feature.update.ui

import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus
import java.util.concurrent.Executor

/** Reports a flexible update downloaded by Google Play, so the app can offer a restart. */
class PlayInstallStateWatcher(
    private val manager: AppUpdateManager,
    private val callbacks: Executor
) : InstallStateWatcher {

    private var listener: InstallStateUpdatedListener? = null

    override fun start(onDownloaded: () -> Unit) {
        stop()
        val current = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) onDownloaded()
        }
        listener = current
        manager.registerListener(current)
        // A download that finished while the app was away has no event left to report it.
        manager.appUpdateInfo.addOnSuccessListener(callbacks) { info ->
            if (listener === current && info.installStatus() == InstallStatus.DOWNLOADED) onDownloaded()
        }
    }

    override fun stop() {
        listener?.let(manager::unregisterListener)
        listener = null
    }

    override fun completeUpdate() {
        manager.completeUpdate()
    }
}
