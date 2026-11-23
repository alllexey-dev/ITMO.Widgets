package dev.alllexey.itmowidgets.feature.update.data

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateAvailability
import dev.alllexey.itmowidgets.feature.update.startableUpdateType
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Play decides whether the Google Play build offers an update. Backend's latest version is shared with the
 * GitHub build and is ahead of Play during a review, a staged rollout, or for a release that never reaches the
 * production track, so the offer waits until Play has an in-app update of the needed type for this device.
 *
 * A build below the minimum version waits the same way: a forced screen that cannot update would be a dead end.
 * An install Play does not own (sideloaded, no Play Store) is never offered an update.
 *
 * @param callbacks runs the Play task callback; the main thread in the app.
 */
class PlayUpdateAvailability(
    private val manager: AppUpdateManager,
    private val callbacks: Executor,
    private val diagnostics: AppDiagnostics
) : AppUpdateAvailability {

    override suspend fun isAvailable(update: AppUpdate): Boolean =
        updateInfo()?.startableUpdateType(update.unsupported) != null

    private suspend fun updateInfo(): AppUpdateInfo? = suspendCancellableCoroutine { continuation ->
        manager.appUpdateInfo.addOnCompleteListener(callbacks) { task ->
            if (!task.isSuccessful) diagnostics.warn(TAG, "Google Play did not report an update state", task.exception)
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val TAG = "AppUpdate"
    }
}
