package me.alllexey123.itmowidgets.providers

import android.content.Context
import androidx.preference.PreferenceManager
import me.alllexey123.itmowidgets.utils.PreferencesStorage

object StorageProvider {
    var storageSingleton: PreferencesStorage? = null

    fun getStorage(context: Context): PreferencesStorage {
        storageSingleton?.let { return it }

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val storage = PreferencesStorage(prefs)
        this.storageSingleton = storage

        return storage
    }

}