package dev.alllexey.itmowidgets

import android.content.Context
import androidx.preference.PreferenceManager
import api.myitmo.MyItmo
import com.google.gson.Gson
import dev.alllexey.itmowidgets.api.ItmoWidgetsBackend
import dev.alllexey.itmowidgets.data.PreferencesStorage
import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.local.QrBitmapCache
import dev.alllexey.itmowidgets.data.local.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.repository.QrCodeRepository
import dev.alllexey.itmowidgets.data.repository.ScheduleRepository
import dev.alllexey.itmowidgets.ui.widgets.data.LessonListRepository
import dev.alllexey.itmowidgets.util.QrBitmapRenderer
import dev.alllexey.itmowidgets.util.QrCodeGenerator
import java.io.File

class AppContainer(context: Context) {

    val storage: PreferencesStorage by lazy {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        PreferencesStorage(prefs)
    }

    val gson: Gson by lazy { myItmo.gson }

    val myItmo: MyItmo by lazy {
        MyItmo().apply {
            this.storage = this@AppContainer.storage
        }
    }

    val backend: ItmoWidgetsBackend by lazy {
        ItmoWidgetsBackend(this)
    }

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

    val qrCodeRepository by lazy {
        QrCodeRepository(
            QrCodeLocalDataSourceImpl(
                context = context
            ), QrCodeRemoteDataSourceImpl(
                myItmo = myItmo
            )
        )
    }

    val qrCodeGenerator by lazy { QrCodeGenerator() }

    val qrBitmapRenderer by lazy { QrBitmapRenderer(context) }

    val qrBitmapCache: QrBitmapCache by lazy {
        QrBitmapCacheImpl(context)
    }

}