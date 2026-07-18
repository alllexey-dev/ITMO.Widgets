package dev.alllexey.itmowidgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import javax.inject.Inject

@HiltAndroidApp
class ItmoWidgetsApplication : Application() {

    @Inject
    lateinit var myItmoStorage: MyItmoStorage

    @Inject
    lateinit var storage: AppSettingsStorage

    override fun onCreate() {
        super.onCreate()
    }
}
