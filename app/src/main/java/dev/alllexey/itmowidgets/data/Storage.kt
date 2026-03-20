package dev.alllexey.itmowidgets.data

import android.content.Context
import android.content.SharedPreferences

class Storage(val prefs: SharedPreferences, val context: Context) {

    val settings: UserSettingsStorage by lazy {
        UserSettingsStorage(prefs)
    }

    val myItmo: MyItmoStorage by lazy {
        MyItmoStorage(prefs)
    }

    val utility: UtilityStorage by lazy {
        UtilityStorage(prefs, context)
    }
}