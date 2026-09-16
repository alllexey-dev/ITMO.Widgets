package dev.alllexey.itmowidgets.core.diagnostics

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAppDiagnosticsTest {

    private val directory: File = Files.createTempDirectory("diagnostics").toFile()
    private val clock = Clock.fixed(Instant.parse("2026-09-16T09:00:00Z"), ZoneOffset.UTC)

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `records survive a restart newest first and never store the secret`() = runTest {
        val first = FileAppDiagnostics(directory, clock)
        first.warn("Sync", "first")
        first.error("Push", "token=eyJhbGciOiJSUzI1NiJ9.eyJpc3UiOjF9.c2ln", IllegalStateException("boom"))
        first.awaitWrites()

        val reopened = FileAppDiagnostics(directory, clock)
        val entries = reopened.observe().first()

        assertEquals(listOf("Push", "Sync"), entries.map { it.tag })
        assertEquals(DiagnosticLevel.ERROR, entries.first().level)
        assertEquals("token=[redacted]", entries.first().message)
        assertTrue(entries.first().stackTrace!!.contains("IllegalStateException: boom"))
        assertNull(entries.last().stackTrace)
        assertFalse(File(directory, "log.jsonl").readText().contains("eyJhbGci"))
    }

    @Test
    fun `the journal is bounded and clear removes the file`() = runTest {
        val diagnostics = FileAppDiagnostics(directory, clock)
        repeat(FileAppDiagnostics.MAX_ENTRIES + 25) { diagnostics.warn("Tag", "message $it") }
        diagnostics.awaitWrites()

        val entries = diagnostics.observe().first()
        assertEquals(FileAppDiagnostics.MAX_ENTRIES, entries.size)
        assertEquals("message ${FileAppDiagnostics.MAX_ENTRIES + 24}", entries.first().message)
        assertEquals(FileAppDiagnostics.MAX_ENTRIES, File(directory, "log.jsonl").readLines().size)

        diagnostics.clear()

        assertTrue(diagnostics.observe().first().isEmpty())
        assertFalse(File(directory, "log.jsonl").exists())
    }

    @Test
    fun `a crash written synchronously is imported on the next start`() = runTest {
        val crashed = FileAppDiagnostics(directory, clock)
        crashed.recordCrash("main", RuntimeException("Bearer abc.def.ghi"))
        assertTrue(File(directory, "pending_crash.jsonl").exists())

        val next = FileAppDiagnostics(directory, clock)
        next.importPendingCrashes()
        next.awaitWrites()

        val entry = next.observe().first().single()
        assertEquals(DiagnosticLevel.CRASH, entry.level)
        assertEquals("Crash: main", entry.tag)
        assertEquals("RuntimeException: Bearer [redacted]", entry.message)
        assertFalse(entry.stackTrace!!.contains("abc.def.ghi"))
        assertFalse(File(directory, "pending_crash.jsonl").exists())
    }

    @Test
    fun `corrupt lines are skipped instead of hiding the journal`() = runTest {
        directory.mkdirs()
        File(directory, "log.jsonl").writeText("not json\n{\"at\":\"2026-09-16T08:00:00Z\",\"level\":\"WARNING\",\"tag\":\"T\",\"message\":\"ok\"}\n")

        val entries = FileAppDiagnostics(directory, clock).observe().first()

        assertEquals(listOf("ok"), entries.map { it.message })
    }
}
