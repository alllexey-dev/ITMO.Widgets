package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.diagnostics.AppLog

/** Developer log of one test: every line as `LEVEL:tag:message`, oldest first. */
class RecordingAppLog : AppLog {
    val lines = mutableListOf<String>()

    override fun info(tag: String, message: String) = record("INFO", tag, message)

    override fun warn(tag: String, message: String, error: Throwable?) = record("WARN", tag, message)

    override fun error(tag: String, message: String, error: Throwable?) = record("ERROR", tag, message)

    private fun record(level: String, tag: String, message: String) {
        lines += "$level:$tag:$message"
    }
}
