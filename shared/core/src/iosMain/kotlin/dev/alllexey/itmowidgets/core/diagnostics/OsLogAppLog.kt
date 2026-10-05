package dev.alllexey.itmowidgets.core.diagnostics

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import platform.Foundation.NSBundle
import platform.darwin.OS_LOG_TYPE_DEFAULT
import platform.darwin.OS_LOG_TYPE_ERROR
import platform.darwin.OS_LOG_TYPE_INFO
import platform.darwin.__dso_handle
import platform.darwin._os_log_internal
import platform.darwin.os_log_create
import platform.darwin.os_log_t
import platform.darwin.os_log_type_t

/**
 * The iOS [AppLog]: unified logging (`os_log`), subsystem = this process's bundle ID, category = the tag; read
 * with Console.app or `log stream --predicate 'subsystem BEGINSWITH "dev.alllexey"'`. Like logcat it is a
 * developer log: callers never pass a token or its payload, and an error contributes its type and message only.
 */
class OsLogAppLog(
    private val subsystem: String = NSBundle.mainBundle.bundleIdentifier ?: FALLBACK_SUBSYSTEM
) : AppLog {

    private val guard = SynchronizedObject()
    private val logs = HashMap<String, os_log_t>()

    override fun info(tag: String, message: String) = write(OS_LOG_TYPE_INFO, tag, message, null)

    override fun warn(tag: String, message: String, error: Throwable?) = write(OS_LOG_TYPE_DEFAULT, tag, message, error)

    override fun error(tag: String, message: String, error: Throwable?) = write(OS_LOG_TYPE_ERROR, tag, message, error)

    @OptIn(ExperimentalForeignApi::class)
    private fun write(type: os_log_type_t, tag: String, message: String, error: Throwable?) {
        _os_log_internal(__dso_handle.ptr, log(tag), type, "%{public}s", line(message, error))
    }

    private fun log(tag: String): os_log_t = synchronized(guard) {
        logs.getOrPut(tag) { os_log_create(subsystem, tag) }
    }

    internal companion object {

        /** Without a bundle ID (a Kotlin/Native test binary). */
        const val FALLBACK_SUBSYSTEM = "Shared"

        fun line(message: String, error: Throwable?): String =
            if (error == null) message else "$message: ${error::class.simpleName}: ${error.message}"
    }
}
