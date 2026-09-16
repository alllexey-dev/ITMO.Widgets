package dev.alllexey.itmowidgets.core.diagnostics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Debug-host stand-in for screens built without Hilt. */
object NoDiagnostics : AppDiagnostics {
    override fun warn(tag: String, message: String, error: Throwable?) = Unit
    override fun error(tag: String, message: String, error: Throwable?) = Unit
    override fun observe(): Flow<List<DiagnosticEntry>> = flowOf(emptyList())
    override suspend fun clear() = Unit
}
