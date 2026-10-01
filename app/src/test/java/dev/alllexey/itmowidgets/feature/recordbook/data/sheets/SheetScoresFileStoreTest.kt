package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import com.google.gson.Gson
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SheetScoresFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "sheet_scores")
    private val store get() = SheetScoresFileStore(directory, Gson())
    private val connection = StoredSheetConnection(
        subjectId = 1, subjectName = "Тестовый предмет", periodKey = "2026-1",
        url = "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid=22",
        tabGid = 22, tabName = "P3110", rowKey = "123456", keyColumn = 0, keyKind = "ISU",
        headerPath = "ИТОГО баллов", columnIndex = 11, value = "66,3", baseline = "66,3", tracked = true,
        status = "OK", updatedAt = 20L, connectedAt = 10L,
    )

    @Test fun `the written state reads back and no temporary file is left`() {
        val state = StoredSheetScores(owner = 123456, connections = listOf(connection, connection.copy(subjectId = 2, value = null, updatedAt = null)))

        store.write(state)

        assertEquals(state, store.read())
        assertEquals("66,3", store.read()!!.connections.first().toModel().value)
        assertFalse(File(directory, "state.json.tmp").exists())
    }

    @Test fun `a missing file reads as null and clear removes the directory`() {
        assertNull(store.read())
        store.write(StoredSheetScores())

        store.clear()

        assertFalse(directory.exists())
        assertNull(store.read())
    }

    @Test fun `another format, garbage and a connection without an address throw`() {
        directory.mkdirs()
        val file = File(directory, "state.json")

        file.writeText("""{"format":2,"connections":[]}""")
        assertThrows(IllegalStateException::class.java) { store.read() }

        file.writeText("not json at all {")
        assertThrows(Exception::class.java) { store.read() }

        file.writeText(Gson().toJson(StoredSheetScores(connections = listOf(connection))).replace("\"url\":\"https", "\"nourl\":\"https"))
        assertThrows(IllegalStateException::class.java) { store.read() }

        file.writeText(Gson().toJson(StoredSheetScores(connections = listOf(connection.copy(keyKind = "PHONE")))))
        assertThrows(IllegalArgumentException::class.java) { store.read() }
    }
}
