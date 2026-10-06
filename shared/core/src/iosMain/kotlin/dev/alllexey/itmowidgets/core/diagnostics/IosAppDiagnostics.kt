package dev.alllexey.itmowidgets.core.diagnostics

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The iOS [AppDiagnostics] until the diagnostics screen and its crash hook arrive (IO-08a): the newest [MAX_ENTRIES]
 * records of this process, newest first, each also written to [log]. Nothing is kept across launches yet.
 *
 * Android's journal sanitises messages and stack traces with `DiagnosticSanitizer`, which is still in `:app`. Until
 * it is shared, an error contributes only its type here: an exception message can carry a token.
 */
class IosAppDiagnostics(
    private val clock: Clock,
    private val log: AppLog
) : AppDiagnostics {

    private val entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())

    override fun warn(tag: String, message: String, error: Throwable?) =
        record(DiagnosticLevel.WARNING, tag, message, error)

    override fun error(tag: String, message: String, error: Throwable?) =
        record(DiagnosticLevel.ERROR, tag, message, error)

    override fun observe(): Flow<List<DiagnosticEntry>> = entries

    override suspend fun clear() {
        entries.value = emptyList()
    }

    private fun record(level: DiagnosticLevel, tag: String, message: String, error: Throwable?) {
        val text = if (error == null) message else "$message: ${error::class.simpleName}"
        when (level) {
            DiagnosticLevel.WARNING -> log.warn(tag, text)
            else -> log.error(tag, text)
        }
        val entry = DiagnosticEntry(at = clock.now(), level = level, tag = tag, message = text, stackTrace = null)
        entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
    }

    companion object {
        /** As Android's journal. */
        const val MAX_ENTRIES = 200
    }
}
