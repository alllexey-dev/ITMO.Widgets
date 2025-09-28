package me.alllexey123.itmowidgets

import android.app.Application

class ItmoWidgetsApp : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}