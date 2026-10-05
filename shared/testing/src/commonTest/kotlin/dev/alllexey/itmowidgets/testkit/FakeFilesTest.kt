package dev.alllexey.itmowidgets.testkit

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FakeFilesTest {
    @Test
    fun holdsTheGivenFilesWithTheirDirectories() {
        val clock = FakeClock(Instant.parse("2026-09-01T09:00:00Z"))
        val files = fakeFileSystemOf("/cache/schedule.json" to "[]", "/prefs.txt" to "Иванов", clock = clock)

        assertEquals("[]", files.read("/cache/schedule.json".toPath()) { readUtf8() })
        assertEquals("Иванов", files.read("/prefs.txt".toPath()) { readUtf8() })
        assertEquals(clock.now().toEpochMilliseconds(), files.metadata("/prefs.txt".toPath()).lastModifiedAtMillis)
        files.checkNoOpenFiles()
    }
}
