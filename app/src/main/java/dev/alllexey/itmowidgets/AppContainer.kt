package dev.alllexey.itmowidgets

import android.content.Context
import androidx.preference.PreferenceManager
import api.myitmo.MyItmo
import com.google.gson.Gson
import dev.alllexey.itmowidgets.api.ItmoWidgetsClient
import dev.alllexey.itmowidgets.data.Storage
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.repository.ErrorLogRepository
import dev.alllexey.itmowidgets.data.repository.ScheduleRepository
import dev.alllexey.itmowidgets.ui.widgets.data.LessonListRepository
import dev.alllexey.itmowidgets.util.ColorUtil
import dev.alllexey.itmowidgets.util.qr.QrToolkit
import java.io.File

class AppContainer(val context: Context) {

    val storage: Storage by lazy {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        Storage(prefs, context)
    }

    val myItmo: MyItmo by lazy {
        MyItmo().apply {
            this.storage = this@AppContainer.storage.myItmo
        }
    }

    val itmoWidgets: ItmoWidgetsClient by lazy {
        ItmoWidgetsClient(myItmo, storage.itmoWidgets, storage.settings)
    }

    val qrToolkit: QrToolkit by lazy {
        QrToolkit(this)
    }

    val gson: Gson by lazy { myItmo.gson }

    val lessonListRepository by lazy { LessonListRepository(context) }

    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(
            localDataSource = ScheduleLocalDataSourceImpl(
                gson = gson,
                cacheDir = File(context.cacheDir, "schedule_cache").apply { mkdirs() }
            ),
            remoteDataSource = ScheduleRemoteDataSourceImpl(
                myItmoApi = myItmo.api()
            )
        )
    }

    val colorUtil: ColorUtil by lazy { ColorUtil(context) }

    val errorLogRepository by lazy { ErrorLogRepository(context) }
}