package dev.alllexey.itmowidgets

import android.app.Application

class ItmoWidgetsApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}