package dev.alllexey.itmowidgets.core.diagnostics

import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class DiagnosticLevel { WARNING, ERROR, CRASH }

data class DiagnosticEntry(
    val at: Instant,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String,
    val stackTrace: String?
) {
    /** One clipboard block: header line, message, optional trace. */
    fun asText(formattedTime: String): String = buildString {
        append(formattedTime).append(' ').append(level.name).append(' ').append(tag)
        append('\n').append(message)
        stackTrace?.let { append('\n').append(it) }
    }
}

/**
 * Local, user-readable record of what went wrong. Nothing here leaves the device
 * unless the user copies it; every text passes [DiagnosticSanitizer] before it is stored.
 *
 * Recording is fire-and-forget so a `catch` in a worker, a push handler or a
 * repository can call it without a coroutine.
 */
interface AppDiagnostics {

    fun warn(tag: String, message: String, error: Throwable? = null)

    fun error(tag: String, message: String, error: Throwable? = null)

    /** Newest first. */
    fun observe(): Flow<List<DiagnosticEntry>>

    suspend fun clear()
}
