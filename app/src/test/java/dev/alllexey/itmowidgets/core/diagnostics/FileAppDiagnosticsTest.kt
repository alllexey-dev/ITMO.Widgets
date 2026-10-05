package dev.alllexey.itmowidgets.core.diagnostics

import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

class FileAppDiagnosticsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val directory: File = Files.createTempDirectory("diagnostics").toFile()
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-16T09:00:00Z")
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `records survive a restart newest first and never store the secret`() = runTest {
        val first = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
        first.warn("Sync", "first")
        first.error("Push", "token=eyJhbGciOiJSUzI1NiJ9.eyJpc3UiOjF9.c2ln", IllegalStateException("boom"))
        first.awaitWrites()

        val reopened = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
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
        val diagnostics = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
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
        val crashed = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
        crashed.recordCrash("main", RuntimeException("Bearer abc.def.ghi"))
        assertTrue(File(directory, "pending_crash.jsonl").exists())

        val next = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
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

        val entries = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog()).observe().first()

        assertEquals(listOf("ok"), entries.map { it.message })
    }

    @Test
    fun `a 2_2 journal reads and its time is written back as 2_2 wrote it`() = runTest {
        val journal22 = File("src/androidTest/assets/upgrade-2.2/files/diagnostics/log.jsonl").readText()
        directory.mkdirs()
        File(directory, "log.jsonl").writeText(journal22)

        val diagnostics = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
        val captured = diagnostics.observe().first().single()
        assertEquals(
            DiagnosticEntry(Instant.parse("2026-10-04T09:00:00Z"), DiagnosticLevel.WARNING, "UpgradeCapture",
                "Synthetic warning of the 2.2 fixture", null),
            captured
        )

        val written = listOf("2026-10-04T09:00:00Z", "2026-10-04T09:00:00.120Z", "2026-10-04T09:00:00.123456Z",
            "2026-10-04T09:00:00.000000001Z", "1970-01-01T00:00:00Z")
        written.forEachIndexed { index, text ->
            val at = java.time.Instant.parse(text)
            val writer = FileAppDiagnostics(directory, object : Clock { override fun now() = at.toKotlinInstant() },
                dispatchers, RecordingAppLog())
            writer.warn("Tag", "message $index")
            writer.awaitWrites()
        }
        val lines = File(directory, "log.jsonl").readLines()
        assertEquals(journal22.trimEnd('\n'), lines.first())
        assertEquals(written.map { java.time.Instant.parse(it).toString() }, lines.drop(1).map { AT.find(it)!!.groupValues[1] })
    }

    @Test
    fun `lines keep the 2_2 fields and leave a missing stack trace out`() = runTest {
        val diagnostics = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog())
        diagnostics.warn("Sync", "plain <a&b='c'>")
        diagnostics.error("Push", "failed", IllegalStateException("boom"))
        diagnostics.awaitWrites()

        val lines = File(directory, "log.jsonl").readLines().map { Json.parseToJsonElement(it).jsonObject }
        assertEquals(setOf("at", "level", "tag", "message"), lines[0].keys)
        assertEquals("plain <a&b='c'>", lines[0].getValue("message").jsonPrimitive.content)
        assertEquals(setOf("at", "level", "tag", "message", "stackTrace"), lines[1].keys)
        assertEquals("2026-09-16T09:00:00Z", lines[1].getValue("at").jsonPrimitive.content)
        assertEquals("ERROR", lines[1].getValue("level").jsonPrimitive.content)
    }

    @Test
    fun `lines 2_2 wrote with any field order, escapes or unknown fields still read`() = runTest {
        directory.mkdirs()
        File(directory, "log.jsonl").writeText(
            """{"message":"a \u003cb\u003e \u0026 \u003d","tag":"T","level":"ERROR","at":"2026-09-16T08:00:00Z","stackTrace":"trace","extra":1}""" + "\n" +
                """{"at":"2026-09-16T08:30:00Z","level":"CRASH"}""" + "\n" +
                """{"at":"not a time","level":"WARNING","tag":"T","message":"skipped"}""" + "\n" +
                """{"at":"2026-09-16T08:45:00Z","level":"UNKNOWN","tag":"T","message":"skipped"}""" + "\n"
        )

        val entries = FileAppDiagnostics(directory, clock, dispatchers, RecordingAppLog()).observe().first()

        assertEquals(
            listOf(
                DiagnosticEntry(Instant.parse("2026-09-16T08:30:00Z"), DiagnosticLevel.CRASH, "", "", null),
                DiagnosticEntry(Instant.parse("2026-09-16T08:00:00Z"), DiagnosticLevel.ERROR, "T", "a <b> & =", "trace")
            ),
            entries
        )
    }

    private companion object {
        val AT = Regex(""""at":"([^"]+)"""")
    }
}
