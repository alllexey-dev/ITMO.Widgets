package dev.alllexey.itmowidgets.feature.schedule.data.widget

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScheduleWidgetSnapshotStoreImpl @Inject constructor(
    gson: Gson,
    @ApplicationContext context: Context,
) : ScheduleWidgetSnapshotStore, SessionDataCleaner {

    private val gson = gson.newBuilder().create()
    private val file = AtomicTextFile(
        File(context.noBackupFilesDir, SNAPSHOT_FILE)
    )

    override suspend fun read(): ScheduleWidgetSnapshot = withContext(Dispatchers.IO) {
        val json = file.read() ?: return@withContext ScheduleWidgetSnapshot.loading()
        runCatching {
            gson.fromJson(json, ScheduleWidgetSnapshot::class.java)
        }.getOrNull() ?: ScheduleWidgetSnapshot.loading()
    }

    override suspend fun write(snapshot: ScheduleWidgetSnapshot) {
        withContext(Dispatchers.IO) {
            file.write(gson.toJson(snapshot))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            file.write(null)
        }
    }

    override suspend fun clearSessionData() {
        clear()
    }

    private companion object {
        const val SNAPSHOT_FILE = "widgets/schedule_snapshot.json"
    }
}
