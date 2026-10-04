package dev.alllexey.itmowidgets.core.diagnostics

/**
 * Developer log (logcat on Android), injected so common code does not reach for a platform logger.
 * Unlike [AppDiagnostics] nothing is stored or shown to the user. Never pass a token or its payload.
 */
interface AppLog {
    fun info(tag: String, message: String)

    fun warn(tag: String, message: String, error: Throwable? = null)

    fun error(tag: String, message: String, error: Throwable? = null)
}
