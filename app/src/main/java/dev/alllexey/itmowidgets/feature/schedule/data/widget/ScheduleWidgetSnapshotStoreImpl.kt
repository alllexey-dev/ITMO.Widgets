package dev.alllexey.itmowidgets.feature.schedule.data.widget

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ScheduleWidgetSnapshotStoreImpl @Inject constructor(
    gson: Gson,
    @ApplicationContext context: Context,
    private val settings: AppSettingsStorage,
    private val timeProvider: AcademicTimeProvider,
    private val tokens: SessionTokenStore,
) : ScheduleWidgetSnapshotStore, SessionDataCleaner {

    private val mutex = Mutex()
    private var generation = 0L
    private val gson = gson.newBuilder().create()
    private val file = AtomicTextFile(
        File(context.noBackupFilesDir, SNAPSHOT_FILE)
    )

    override suspend fun read(): ScheduleWidgetSnapshot = withContext(Dispatchers.IO) {
        if (!tokens.hasRefreshToken()) return@withContext ScheduleWidgetSnapshot.signedOut()
        val cached = mutex.withLock { file.read()?.let { it to generation } }
            ?: return@withContext ScheduleWidgetSnapshot.loading()
        val snapshot = runCatching {
            gson.fromJson(cached.first, ScheduleWidgetSnapshot::class.java)
        }.getOrNull() ?: ScheduleWidgetSnapshot.loading()
        val enabled = settings.getScheduleSportAutoSignEnabled() && settings.getCustomServicesEnabled()
        // Settings reads suspend: cleanup/new login may have invalidated the JSON meanwhile.
        mutex.withLock {
            when {
                !tokens.hasRefreshToken() -> ScheduleWidgetSnapshot.signedOut()
                generation != cached.second -> ScheduleWidgetSnapshot.loading()
                else -> snapshot.forPendingAvailability(enabled, timeProvider.now().toInstant())
            }
        }
    }

    override suspend fun write(snapshot: ScheduleWidgetSnapshot) {
        withContext(Dispatchers.IO) {
            mutex.withLock { file.write(gson.toJson(snapshot)) }
        }
    }

    override suspend fun currentGeneration(): Long = mutex.withLock { generation }

    override suspend fun writeIfCurrent(snapshot: ScheduleWidgetSnapshot, generation: Long): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (this@ScheduleWidgetSnapshotStoreImpl.generation != generation) return@withLock false
                file.write(gson.toJson(snapshot))
                true
            }
        }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
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
        const val SNAPSHOT_FILE = "widgets/schedule_snapshot.json"
    }
}
