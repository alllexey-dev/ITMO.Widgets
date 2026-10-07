package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookStoreJson
import dev.alllexey.itmowidgets.feature.recordbook.data.assertSameJson
import dev.alllexey.itmowidgets.feature.recordbook.data.copyStored22
import java.io.File
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SheetScoresFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val directory get() = File(temporary.root, "sheet_scores")
    private val file get() = File(directory, "state.json")
    private val store get() = SheetScoresFileStore(directory.toOkioPath(), FileSystem.SYSTEM)
    private val connection = StoredSheetConnection(
        subjectId = 1, subjectName = "Тестовый предмет", periodKey = "2026-1",
        url = "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid=22",
        tabGid = 22, tabName = "P3110", rowKey = "123456", keyColumn = 0, keyKind = "ISU",
        headerPath = "ИТОГО баллов", columnIndex = 11, value = "66,3", baseline = "66,3", tracked = true,
        status = "OK", updatedAt = 20L, connectedAt = 10L,
    )
    private val state = StoredSheetScores(
        owner = 123456,
        connections = listOf(connection, connection.copy(subjectId = 2, value = null, baseline = null, updatedAt = null))
    )

    @Test fun `the written state reads back and no temporary file is left`() {
        store.write(state)

        assertEquals(state, store.read())
        assertEquals("66,3", store.read()!!.connections.first().toModel().value)
        assertFalse(File(directory, "state.json.new").exists())
    }

    @Test fun `the 2_2 capture reads, writes back the same document and reads again`() {
        val expected = StoredSheetScores(
            owner = 123456,
            connections = listOf(
                StoredSheetConnection(
                    subjectId = 2001, subjectName = "Тестовая дисциплина", periodKey = "2026-1",
                    url = "https://docs.google.com/spreadsheets/d/upgrade22SyntheticSheetId0001/edit#gid=0",
                    tabGid = 0, tabName = "Баллы", rowKey = "123456", keyColumn = 0, keyKind = "ISU",
                    headerPath = "Итого", columnIndex = 5, value = "71", baseline = "65", tracked = true,
                    status = "OK", updatedAt = 1791104400000, connectedAt = 1791018000000,
                )
            )
        )

        assertRoundTrip("sheet_scores/state.json", expected)
    }

    @Test fun `the 2_2 file with every nullable field absent reads, writes back the same document and reads again`() {
        val url = "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit"
        val expected = StoredSheetScores(
            owner = 300001,
            connections = listOf(
                StoredSheetConnection(
                    subjectId = 42, subjectName = "Тестовый предмет", periodKey = "2026-1", url = "$url#gid=11",
                    tabGid = 11, tabName = "All", rowKey = "300001", keyColumn = 0, keyKind = "ISU",
                    headerPath = "Итого / Баллы", columnIndex = 7, value = "57.5", baseline = "50", tracked = true,
                    status = "OK", updatedAt = 1791180000000, connectedAt = 1790000000000,
                ),
                StoredSheetConnection(
                    subjectId = 43, subjectName = "Second subject «кавычки»", periodKey = "2025-2", url = url,
                    tabGid = 0, tabName = "", rowKey = "Тестов Тест", keyColumn = 1, keyKind = "NAME",
                    headerPath = "", columnIndex = 0, tracked = false, status = "ROW_NOT_FOUND",
                    connectedAt = 1790000500000,
                ),
            )
        )

        assertRoundTrip("sheet_scores/state-sp08.json", expected)
    }

    @Test fun `2_2 reads the written file - the field set is Gson's, format and empty connections included`() {
        store.write(state)
        assertSameJson(GSON_STATE, file.readText())

        store.write(StoredSheetScores(owner = 123456))
        assertEquals("""{"format":1,"owner":123456,"connections":[]}""", file.readText())
    }

    @Test fun `a 2_2 temporary leftover is ignored`() {
        directory.mkdirs()
        File(directory, "state.json.tmp").writeText("{partial")
        assertNull(store.read())

        store.write(state)

        assertEquals(state, store.read())
    }

    @Test fun `a missing file reads as null and clear removes the directory`() {
        assertNull(store.read())
        store.write(StoredSheetScores())

        store.clear()

        assertFalse(directory.exists())
        assertNull(store.read())
    }

    @Test fun `another format, garbage and an invalid connection throw`() {
        directory.mkdirs()

        file.writeText("""{"format":2,"connections":[]}""")
        assertThrows(IllegalStateException::class.java) { store.read() }

        file.writeText("not json at all {")
        assertThrows(Exception::class.java) { store.read() }

        listOf(
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection))).replace("\"url\":\"https", "\"nourl\":\"https"),
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection.copy(url = "https://example.org/sheet")))),
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection.copy(rowKey = " ")))),
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection.copy(keyKind = "PHONE")))),
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection.copy(status = "LOST")))),
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection.copy(columnIndex = -1)))),
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }

    private fun assertRoundTrip(golden: String, expected: StoredSheetScores) {
        val original = copyStored22(golden, directory, "state.json").readText()

        assertEquals(expected, store.read())
        store.write(expected)
        val written = file.readText()
        assertSameJson(original, written)
        assertTrue(written, written.startsWith("""{"format":1,"""))
        assertEquals(expected, SheetScoresFileStore(directory.toOkioPath(), FileSystem.SYSTEM).read())
    }

    private companion object {
        /** What 2.2's `Gson().toJson` wrote for `state`: every non-null field in declaration order, nulls left out. */
        val GSON_STATE = """
            {"format":1,"owner":123456,"connections":[
             {"subjectId":1,"subjectName":"Тестовый предмет","periodKey":"2026-1",
              "url":"https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid\u003d22",
              "tabGid":22,"tabName":"P3110","rowKey":"123456","keyColumn":0,"keyKind":"ISU","headerPath":"ИТОГО баллов",
              "columnIndex":11,"value":"66,3","baseline":"66,3","tracked":true,"status":"OK","updatedAt":20,
              "connectedAt":10},
             {"subjectId":2,"subjectName":"Тестовый предмет","periodKey":"2026-1",
              "url":"https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid\u003d22",
              "tabGid":22,"tabName":"P3110","rowKey":"123456","keyColumn":0,"keyKind":"ISU","headerPath":"ИТОГО баллов",
              "columnIndex":11,"tracked":true,"status":"OK","connectedAt":10}]}
        """.trimIndent()
    }
}
