package me.alllexey123.itmowidgets.providers

import android.content.Context
import api.myitmo.MyItmo

object MyItmoProvider {

    var myItmoSingleton: MyItmo? = null

    fun getMyItmo(context: Context): MyItmo {
        myItmoSingleton?.let { return it }

        val myItmo = MyItmo()
        val storage = StorageProvider.getStorage(context)
        myItmo.storage = storage

        this.myItmoSingleton = myItmo
        return myItmo
    }

}