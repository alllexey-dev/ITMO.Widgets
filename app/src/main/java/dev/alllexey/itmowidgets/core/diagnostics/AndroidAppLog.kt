package dev.alllexey.itmowidgets.core.diagnostics

import android.util.Log

class AndroidAppLog : AppLog {
    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        if (error == null) Log.e(tag, message) else Log.e(tag, message, error)
    }
}
