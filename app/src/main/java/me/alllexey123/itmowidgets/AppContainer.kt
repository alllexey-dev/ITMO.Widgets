package me.alllexey123.itmowidgets

import android.content.Context
import androidx.preference.PreferenceManager
import api.myitmo.MyItmo
import com.google.gson.Gson
import me.alllexey123.itmowidgets.data.local.QrCodeLocalDataSourceImpl
import me.alllexey123.itmowidgets.data.local.ScheduleLocalDataSourceImpl
import me.alllexey123.itmowidgets.data.remote.QrCodeRemoteDataSourceImpl
import me.alllexey123.itmowidgets.data.remote.ScheduleRemoteDataSourceImpl
import me.alllexey123.itmowidgets.data.repository.QrCodeRepository
import me.alllexey123.itmowidgets.data.repository.ScheduleRepository
import me.alllexey123.itmowidgets.data.PreferencesStorage
import me.alllexey123.itmowidgets.ui.widgets.data.LessonListRepository
import me.alllexey123.itmowidgets.util.QrBitmapRenderer
import me.alllexey123.itmowidgets.util.QrCodeGenerator
import java.io.File
import kotlin.getValue

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


}