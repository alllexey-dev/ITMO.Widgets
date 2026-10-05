package dev.alllexey.itmowidgets.feature.update.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
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
    private val backend: BackendGate,
    private val utilityStorage: UtilityStorage,
    private val installedVersion: AppVersionName,
    private val clock: Clock,
    private val diagnostics: AppDiagnostics,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : AppUpdateRepository {

    /** The demo session never offers an update: it asks nothing from Backend. */
    override suspend fun loadUpdate(): AppUpdate? {
        if (demo.isActive()) return null
        if (!backend.mayCallBackend()) return null
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
        notifiedAt = Instant.fromEpochMilliseconds(utilityStorage.getVersionNotificationTimestamp())
    )

    override suspend fun markNotified() {
        utilityStorage.setVersionNotificationTimestamp(clock.now().toEpochMilliseconds())
    }

    override suspend fun skip(version: AppVersionName) {
        utilityStorage.setSkippedVersion(version.raw)
    }

    private suspend fun fetchVersionInfo() = try {
        withContext(dispatchers.io) { widgetsApi.appVersionInfo().data }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        diagnostics.warn(TAG, "Failed to read the latest app version", error)
        null
    }

    private companion object {
        const val TAG = "AppUpdate"
    }
}
