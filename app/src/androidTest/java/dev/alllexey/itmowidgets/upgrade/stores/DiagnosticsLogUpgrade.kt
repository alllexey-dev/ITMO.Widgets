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

/** `files/diagnostics/log.jsonl`: the 2.2 entry stays in the list the diagnostics screen shows. */
object DiagnosticsLogUpgrade {

    fun check(fixture: Upgrade22Fixture): Unit = runBlocking {
        val diagnostics = FileAppDiagnostics(
            File(fixture.filesDir, "diagnostics"),
            object : Clock { override fun now() = fixture.clock.instant().toKotlinInstant() },
            DeviceDispatchers,
            AndroidAppLog()
        )
        assertEquals(
            listOf(
                DiagnosticEntry(
                    at = Captured22.AT.toKotlinInstant(),
                    level = DiagnosticLevel.WARNING,
                    tag = "UpgradeCapture",
                    message = "Synthetic warning of the 2.2 fixture",
                    stackTrace = null
                )
            ),
            diagnostics.observe().first()
        )
    }
}
