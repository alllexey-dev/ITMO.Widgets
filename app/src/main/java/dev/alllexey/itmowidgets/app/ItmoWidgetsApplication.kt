package dev.alllexey.itmowidgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.FcmWork

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppNotificationChannels.create(this)
        FcmWork.syncToken(this)
    }
}
