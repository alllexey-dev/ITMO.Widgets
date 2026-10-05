package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.diagnostics.AndroidAppLog
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticEntry
import dev.alllexey.itmowidgets.core.diagnostics.DiagnosticLevel
import dev.alllexey.itmowidgets.core.diagnostics.FileAppDiagnostics
import dev.alllexey.itmowidgets.testing.DeviceDispatchers
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

/**
 * `files/diagnostics/log.jsonl`: Gson wrote the 2.2 entry; kotlinx reads it, appends after it and a new instance
 * reads both.
 */
object DiagnosticsLogUpgrade {

    fun check(fixture: Upgrade22Fixture): Unit = runBlocking {
        val captured = DiagnosticEntry(
            at = Captured22.AT.toKotlinInstant(),
            level = DiagnosticLevel.WARNING,
            tag = "UpgradeCapture",
            message = "Synthetic warning of the 2.2 fixture",
            stackTrace = null
        )
        val diagnostics = diagnostics(fixture)
        assertEquals(listOf(captured), diagnostics.observe().first())

        diagnostics.warn("UpgradeCheck", "Synthetic warning after the upgrade")
        diagnostics.awaitWrites()

        assertEquals(
            listOf("UpgradeCheck" to "Synthetic warning after the upgrade", "UpgradeCapture" to captured.message),
            diagnostics(fixture).observe().first().map { it.tag to it.message }
        )
        assertEquals(captured, diagnostics(fixture).observe().first().last())
    }

    private fun diagnostics(fixture: Upgrade22Fixture) = FileAppDiagnostics(
        File(fixture.filesDir, "diagnostics"),
        object : Clock { override fun now() = fixture.clock.instant().toKotlinInstant() },
        DeviceDispatchers,
        AndroidAppLog()
    )
}
