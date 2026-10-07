package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the app runs on every return to the foreground (IO-13a): it reads the notification settings, which the
 * extensions' session snapshot follows, then syncs the push registration, which reaches Backend only when the
 * token, the account or the alerts answer changed. Swift calls [refresh]; a failure is logged here, since an
 * exception thrown into Swift would end the app.
 */
class PushForegroundRefresh(
    private val device: IosPushDevice,
    private val tokenSync: FcmTokenSync,
    private val log: AppLog,
) {

    suspend fun refresh() {
        try {
            device.alertsAllowed()
            tokenSync.sync()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.warn(TAG, "Push registration sync failed", error)
        }
    }

    private companion object {
        const val TAG = "PushRegistration"
    }
}
