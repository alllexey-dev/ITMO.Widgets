package me.alllexey123.itmowidgets

import android.content.SharedPreferences
import api.myitmo.MyItmo

object MyItmoProvider {

    var myItmoSingleton: MyItmo? = null

    fun createMyItmo(prefs: SharedPreferences): MyItmo {
        val myItmo = MyItmo()
        val storage = PrefsStorage(prefs)
        myItmo.storage = storage
        return myItmo
    }

}