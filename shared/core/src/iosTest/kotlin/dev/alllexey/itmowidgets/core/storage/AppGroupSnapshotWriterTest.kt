package dev.alllexey.itmowidgets.core.storage

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okio.FileSystem

class AppGroupSnapshotWriterTest {

    private val temporary = TemporaryDirectory()
    private val directory = AppGroupDirectory(temporary.root / "group", isShared = true)
    private val reloaded = mutableListOf<String>()
    private val writer = AppGroupSnapshotWriter(directory, WidgetReloader { reloaded += it })
    private val snapshot = SnapshotFile("lessons", 1, ListSerializer(String.serializer()))

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    @Test
    fun writesAVersionedFileWithItsVersionMarker() {
        writer.write(snapshot, listOf("Math", "Physics"))

        val file = temporary.root / "group" / "lessons-v1.json"
        assertEquals("""{"version":1,"value":["Math","Physics"]}""", FileSystem.SYSTEM.read(file) { readUtf8() })
        assertEquals(listOf("Math", "Physics"), writer.read(snapshot))
    }

    @Test
    fun reloadsTheKindsOnlyOnceTheNewFileIsInPlace() {
        writer.write(snapshot, listOf("old"))
        val seenByReload = mutableListOf<List<String>?>()
        val checkingWriter = AppGroupSnapshotWriter(
            directory,
            WidgetReloader { seenByReload.add(readSnapshot(directory, snapshot)) }
        )

        checkingWriter.write(snapshot, listOf("new"), reloadKinds = listOf("kind.a", "kind.b"))

        assertEquals<List<List<String>?>>(listOf(listOf("new"), listOf("new")), seenByReload)
    }

    @Test
    fun passesTheKindsToTheReloader() {
        writer.write(snapshot, listOf("x"), reloadKinds = listOf("kind.a", "kind.b"))

        assertEquals(listOf("kind.a", "kind.b"), reloaded)
    }

    @Test
    fun replacesTheFileWithoutLeavingTemporaryFiles() {
        repeat(3) { writer.write(snapshot, listOf("value $it")) }

        assertEquals(listOf("lessons-v1.json"), FileSystem.SYSTEM.list(directory.root).map { it.name })
        assertEquals(listOf("value 2"), writer.read(snapshot))
    }

    @Test
    fun aReaderNeverSeesAPartialSnapshot() = runTest {
        val large = List(2_000) { "lesson number $it with a long enough title" }
        writer.write(snapshot, large)

        withContext(Dispatchers.Default) {
            val writes = async { repeat(WRITES) { writer.write(snapshot, large.shuffled()) } }
            var reads = 0
            while (!writes.isCompleted || reads == 0) {
                val read = readSnapshot(directory, snapshot)
                assertEquals(large.size, read?.size, "read $reads saw a partial or missing snapshot")
                reads++
            }
            writes.await()
        }
    }

    @Test
    fun rejectsASnapshotOfAHigherVersion() {
        FileSystem.SYSTEM.createDirectories(directory.root)
        FileSystem.SYSTEM.write(directory.file("lessons-v1.json")) { writeUtf8("""{"version":2,"value":["new"]}""") }

        assertNull(writer.read(snapshot))
    }

    @Test
    fun readsAMissingOrUnreadableSnapshotAsNull() {
        assertNull(writer.read(snapshot))

        FileSystem.SYSTEM.createDirectories(directory.root)
        listOf("not json", """{"value":["no version"]}""", """{"version":1,"value":42}""").forEach { text ->
            FileSystem.SYSTEM.write(directory.file("lessons-v1.json")) { writeUtf8(text) }
            assertNull(writer.read(snapshot), text)
        }
    }

    @Test
    fun refusesABadNameOrVersion() {
        assertFailsWith<IllegalArgumentException> { SnapshotFile("Lessons", 1, String.serializer()) }
        assertFailsWith<IllegalArgumentException> { SnapshotFile("lessons", 0, String.serializer()) }
        assertEquals("qr-code-v3.json", SnapshotFile("qr-code", 3, String.serializer()).fileName)
    }

    private companion object {
        const val WRITES = 50
    }
}
