package dev.alllexey.itmowidgets.core.diagnostics

import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.storage.TemporaryDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem

class IosAppDiagnosticsTest {

    private val temporary = TemporaryDirectory()
    private val path = temporary.root / IosAppDiagnostics.CRASH_FILE
    private val clock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(NOW)
    }

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    @Test
    fun anErrorKeepsOnlyItsTypeAndLastsForTheLaunch() = runTest {
        val diagnostics = IosAppDiagnostics(clock, OsLogAppLog(), AtomicTextFile(path))

        diagnostics.error("Sync", "Refresh failed", IllegalStateException("token=secret"))

        val entry = diagnostics.observe().first().single()
        assertEquals(DiagnosticLevel.ERROR, entry.level)
        assertEquals("Refresh failed: IllegalStateException", entry.message)
        assertFalse(FileSystem.SYSTEM.exists(path), "only a crash is saved for the next launch")
    }

    @Test
    fun aCrashIsSavedAndTheNextLaunchShowsItOnce() = runTest {
        val crashed = IosAppDiagnostics(clock, OsLogAppLog(), AtomicTextFile(path))

        crashed.recordCrash(IllegalArgumentException("token=secret"))

        val recorded = crashed.observe().first().single()
        assertEquals(DiagnosticLevel.CRASH, recorded.level)
        assertEquals("IllegalArgumentException", recorded.message)
        assertTrue(FileSystem.SYSTEM.exists(path))
        assertFalse("secret" in FileSystem.SYSTEM.read(path) { readUtf8() }, "the message never reaches the file")

        val nextLaunch = IosAppDiagnostics(clock, OsLogAppLog(), AtomicTextFile(path))
        val restored = nextLaunch.observe().first().single()
        assertEquals(DiagnosticLevel.CRASH, restored.level)
        assertEquals("IllegalArgumentException", restored.message)
        assertEquals(Instant.fromEpochMilliseconds(NOW), restored.at)
        assertNotNull(restored.stackTrace)
        assertFalse(FileSystem.SYSTEM.exists(path), "the saved crash is shown once")

        val third = IosAppDiagnostics(clock, OsLogAppLog(), AtomicTextFile(path))
        assertTrue(third.observe().first().isEmpty())
    }

    @Test
    fun anUnreadableOrNewerSavedCrashIsDropped() = runTest {
        AtomicTextFile(path).write("not json")
        assertTrue(IosAppDiagnostics(clock, OsLogAppLog(), AtomicTextFile(path)).observe().first().isEmpty())
        assertFalse(FileSystem.SYSTEM.exists(path))

        AtomicTextFile(path).write(
            """{"version":2,"atEpochMillis":$NOW,"message":"Future","stackTrace":null}"""
        )
        assertTrue(IosAppDiagnostics(clock, OsLogAppLog(), AtomicTextFile(path)).observe().first().isEmpty())
    }

    @Test
    fun withoutAFileACrashStaysInMemory() = runTest {
        val diagnostics = IosAppDiagnostics(clock, OsLogAppLog())

        diagnostics.recordCrash(IllegalStateException())

        assertEquals(DiagnosticLevel.CRASH, diagnostics.observe().first().single().level)
    }

    private companion object {
        const val NOW = 1_780_000_000_000L
    }
}
