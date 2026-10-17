package dev.alllexey.itmowidgets.feature.sport.data.widget

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetKind
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshot
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The sport widget's last snapshot in `widgets/sport_snapshot.json` (`formatVersion` 1), so a launcher redraw shows it
 * without a request. A session cleaner: sign-out and a new session start from loading, an old session's file is never
 * shown in the next one.
 */
class SportWidgetSnapshotStore(
    directories: AppDirectories,
    private val tokens: SessionTokenStore,
    private val dispatchers: AppDispatchers,
) : SessionDataCleaner {

    private val mutex = Mutex()
    private var generation = 0L
    private val file = AtomicTextFile(directories.noBackup / FILE)

    suspend fun read(): SportWidgetSnapshot = withContext(dispatchers.io) {
        if (!tokens.hasRefreshToken()) return@withContext SportWidgetSnapshot.of(SportWidgetKind.SIGNED_OUT)
        val text = mutex.withLock { file.read() } ?: return@withContext SportWidgetSnapshot.of(SportWidgetKind.LOADING)
        runCatching { decode(text) }.getOrNull() ?: SportWidgetSnapshot.of(SportWidgetKind.LOADING)
    }

    suspend fun currentGeneration(): Long = mutex.withLock { generation }

    /** Writes unless a session change came since [generation] was read. */
    suspend fun writeIfCurrent(snapshot: SportWidgetSnapshot, generation: Long): Boolean = withContext(dispatchers.io) {
        mutex.withLock {
            if (this@SportWidgetSnapshotStore.generation != generation) return@withLock false
            file.write(encode(snapshot))
            true
        }
    }

    override suspend fun clearSessionData() {
        withContext(dispatchers.io) {
            mutex.withLock {
                generation++
                file.write(null)
            }
        }
    }

    private fun encode(snapshot: SportWidgetSnapshot): String {
        val fields = SportWidgetTimelineJson.json.encodeToJsonElement(SportWidgetSnapshot.serializer(), snapshot).jsonObject
        return JsonObject(mapOf(VERSION_KEY to JsonPrimitive(VERSION)) + fields).toString()
    }

    private fun decode(text: String): SportWidgetSnapshot {
        val root = SportWidgetTimelineJson.json.parseToJsonElement(text).jsonObject
        check(root[VERSION_KEY]?.jsonPrimitive?.int == VERSION) { "Unknown sport widget snapshot" }
        return SportWidgetTimelineJson.json.decodeFromJsonElement(SportWidgetSnapshot.serializer(), JsonObject(root - VERSION_KEY))
    }

    private companion object {
        /** Stable identifier: the path under Android's `noBackupFilesDir`. */
        const val FILE = "widgets/sport_snapshot.json"
        const val VERSION_KEY = "formatVersion"
        const val VERSION = 1
    }
}
