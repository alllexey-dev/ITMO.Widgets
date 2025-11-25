package dev.alllexey.itmowidgets

import android.app.Application
import dev.alllexey.itmowidgets.util.GlobalExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ItmoWidgetsApp : Application() {

    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler(
            GlobalExceptionHandler(this, oldHandler)
        )

        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            appContainer.errorLogRepository.checkPendingCrashes()
        }
    }
}