package dev.alllexey.itmowidgets.feature.update.data

import android.util.Log
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads release metadata from the project backend.
 *
 * The check is gated on the backend opt-in like every other backend call: the
 * endpoint itself is unauthenticated, but the client attaches the MyITMO token
 * to whatever it sends there.
 */
class AppUpdateRepositoryImpl @Inject constructor(
    private val widgetsApi: ItmoWidgetsApi,
    private val customServices: CustomServicesRepository,
    private val utilityStorage: UtilityStorage,
    private val installedVersion: AppVersionName,
    @param:WallClock private val clock: Clock
) : AppUpdateRepository {

    override suspend fun loadUpdate(): AppUpdate? {
        if (!customServices.isEnabled()) return null
        val info = fetchVersionInfo() ?: return null
        val latest = AppVersionName(info.latestVersion)
        if (latest <= installedVersion) return null
        return AppUpdate(
            installed = installedVersion,
            latest = latest,
            note = info.note.trim(),
            unsupported = installedVersion < AppVersionName(info.minVersion)
        )
    }

    override suspend fun reminder(): AppUpdateReminder = AppUpdateReminder(
        skippedVersion = AppVersionName(utilityStorage.getSkippedVersion()),
        notifiedAt = Instant.ofEpochMilli(utilityStorage.getVersionNotificationTimestamp())
    )

    override suspend fun markNotified() {
        utilityStorage.setVersionNotificationTimestamp(clock.millis())
    }

    override suspend fun skip(version: AppVersionName) {
        utilityStorage.setSkippedVersion(version.raw)
    }

    private suspend fun fetchVersionInfo() = try {
        withContext(Dispatchers.IO) { widgetsApi.appVersionInfo().data }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.w(TAG, "Failed to read the latest app version", error)
        null
    }

    private companion object {
        const val TAG = "AppUpdate"
    }
}
