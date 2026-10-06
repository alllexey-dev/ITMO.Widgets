package dev.alllexey.itmowidgets.feature.schedule.data.widget

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleStoreJson
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The JSON of `widgets/schedule_snapshot.json`. 2: kotlinx with a top-level `formatVersion`, always written first. A
 * file without it is 2.2's Gson snapshot: the same keys and enum names, nulls omitted, text sizes possibly absent.
 * 2.2 ignores the marker, so either build reads the other's file.
 */
internal object ScheduleWidgetSnapshotJson {
    private const val FORMAT_VERSION_KEY = "formatVersion"
    private const val FORMAT_VERSION = 2

    fun encode(snapshot: ScheduleWidgetSnapshot): String {
        val fields = ScheduleStoreJson.encodeToJsonElement(ScheduleWidgetSnapshot.serializer(), snapshot).jsonObject
        val versioned = JsonObject(mapOf(FORMAT_VERSION_KEY to JsonPrimitive(FORMAT_VERSION)) + fields)
        return ScheduleStoreJson.encodeToString(JsonObject.serializer(), versioned)
    }

    /** Throws on a corrupt file or one of another version. */
    fun decode(text: String): ScheduleWidgetSnapshot {
        val root = ScheduleStoreJson.parseToJsonElement(text).jsonObject
        val version = root[FORMAT_VERSION_KEY]?.jsonPrimitive?.int
        check(version == null || version == FORMAT_VERSION) { "Unknown schedule widget snapshot version $version" }
        return ScheduleStoreJson.decodeFromJsonElement(ScheduleWidgetSnapshot.serializer(), root)
    }
}

@Singleton
class ScheduleWidgetSnapshotStoreImpl @Inject constructor(
    directories: AppDirectories,
    private val scheduleChecks: ScheduleCheckPreferences,
    private val backend: BackendGate,
    private val timeProvider: AcademicTimeProvider,
    private val tokens: SessionTokenStore,
    private val dispatchers: AppDispatchers,
) : ScheduleWidgetSnapshotStore, SessionDataCleaner {

    private val mutex = Mutex()
    private var generation = 0L
    private val file = AtomicTextFile(directories.noBackup / SNAPSHOT_FILE)

    override suspend fun read(): ScheduleWidgetSnapshot = withContext(dispatchers.io) {
        if (!tokens.hasRefreshToken()) return@withContext ScheduleWidgetSnapshot.signedOut()
        val cached = mutex.withLock { file.read()?.let { it to generation } }
            ?: return@withContext ScheduleWidgetSnapshot.loading()
        val snapshot = runCatching {
            ScheduleWidgetSnapshotJson.decode(cached.first)
        }.getOrNull() ?: ScheduleWidgetSnapshot.loading()
        val enabled = scheduleChecks.getScheduleSportAutoSignEnabled() && backend.isOptedIn()
        // Settings reads suspend: cleanup/new login may have invalidated the JSON meanwhile.
        mutex.withLock {
            when {
                !tokens.hasRefreshToken() -> ScheduleWidgetSnapshot.signedOut()
                generation != cached.second -> ScheduleWidgetSnapshot.loading()
                else -> snapshot.forPendingAvailability(enabled, timeProvider.now())
            }
        }
    }

    override suspend fun write(snapshot: ScheduleWidgetSnapshot) {
        withContext(dispatchers.io) {
            mutex.withLock { file.write(ScheduleWidgetSnapshotJson.encode(snapshot)) }
        }
    }

    override suspend fun currentGeneration(): Long = mutex.withLock { generation }

    override suspend fun writeIfCurrent(snapshot: ScheduleWidgetSnapshot, generation: Long): Boolean =
        withContext(dispatchers.io) {
            mutex.withLock {
                if (this@ScheduleWidgetSnapshotStoreImpl.generation != generation) return@withLock false
                file.write(ScheduleWidgetSnapshotJson.encode(snapshot))
                true
            }
        }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            mutex.withLock {
                generation++
                file.write(null)
            }
        }
    }

    override suspend fun clearSessionData() {
        clear()
    }

    private companion object {
        /** Stable identifier: the path under Android's `noBackupFilesDir` that placed widgets read after an update. */
        const val SNAPSHOT_FILE = "widgets/schedule_snapshot.json"
    }
}
