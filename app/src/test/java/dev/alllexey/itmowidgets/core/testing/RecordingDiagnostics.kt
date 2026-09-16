package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory journal for tests: records synchronously, newest first. */
class RecordingDiagnostics : AppDiagnostics {
    val entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())

    val messages: List<String> get() = entries.value.map { "${it.level}:${it.tag}:${it.message}" }

    override fun warn(tag: String, message: String, error: Throwable?) = record(DiagnosticLevel.WARNING, tag, message, error)

    override fun error(tag: String, message: String, error: Throwable?) = record(DiagnosticLevel.ERROR, tag, message, error)

    override fun observe(): Flow<List<DiagnosticEntry>> = entries

    override suspend fun clear() {
        entries.value = emptyList()
    }

    private fun record(level: DiagnosticLevel, tag: String, message: String, error: Throwable?) {
        entries.value = listOf(DiagnosticEntry(Instant.EPOCH, level, tag, message, error?.toString())) + entries.value
    }
}
