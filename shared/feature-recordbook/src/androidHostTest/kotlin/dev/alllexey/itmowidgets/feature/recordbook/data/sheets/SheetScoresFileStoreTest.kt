package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookStoreJson
import dev.alllexey.itmowidgets.feature.recordbook.data.copyStored22
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.BlankPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
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
    private fun direct(gid: Long, path: String, index: Int) = SheetFormula.Direct(SheetCellRef(gid, SheetColumnRef(path, index))).toStored()
    private val connection = StoredSheetConnection(
        subjectId = 1, subjectName = "Тестовый предмет", periodKey = "2026-1",
        url = "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit#gid=22",
        tabGid = 22, tabName = "P3110", rowKey = "123456", keyColumn = 0, keyKind = "ISU",
        formula = direct(22, "ИТОГО баллов", 11), value = "66,3", baseline = "66,3", tracked = true,
        status = "OK", updatedAt = 20L, connectedAt = 10L,
    )
    private val state = StoredSheetScores(
        owner = 123456,
        connections = listOf(connection, connection.copy(subjectId = 2, value = null, baseline = null, updatedAt = null))
    )

    @Test fun `the written state reads back as format 2 and no temporary file is left`() {
        store.write(state)

        assertEquals(state, store.read())
        assertEquals("66,3", store.read()!!.connections.first().toModel().value)
        assertTrue(file.readText().startsWith("""{"format":2,"""))
        assertFalse(File(directory, "state.json.new").exists())
    }

    @Test fun `the 2_2 capture of format 1 reads as a direct formula of its tab and writes format 2`() {
        val expected = StoredSheetScores(
            owner = 123456,
            connections = listOf(
                StoredSheetConnection(
                    subjectId = 2001, subjectName = "Тестовая дисциплина", periodKey = "2026-1",
                    url = "https://docs.google.com/spreadsheets/d/upgrade22SyntheticSheetId0001/edit#gid=0",
                    tabGid = 0, tabName = "Баллы", rowKey = "123456", keyColumn = 0, keyKind = "ISU",
                    formula = direct(0, "Итого", 5), value = "71", baseline = "65", tracked = true,
                    status = "OK", updatedAt = 1791104400000, connectedAt = 1791018000000,
                )
            )
        )

        assertMigrates("sheet_scores/state.json", expected)
    }

    @Test fun `the 2_2 file with every nullable field absent reads, migrates and reads again`() {
        val url = "https://docs.google.com/spreadsheets/d/1TestSheetIdForUnitTests_0123456789-abc/edit"
        val expected = StoredSheetScores(
            owner = 300001,
            connections = listOf(
                StoredSheetConnection(
                    subjectId = 42, subjectName = "Тестовый предмет", periodKey = "2026-1", url = "$url#gid=11",
                    tabGid = 11, tabName = "All", rowKey = "300001", keyColumn = 0, keyKind = "ISU",
                    formula = direct(11, "Итого / Баллы", 7), value = "57.5", baseline = "50", tracked = true,
                    status = "OK", updatedAt = 1791180000000, connectedAt = 1790000000000,
                ),
                StoredSheetConnection(
                    subjectId = 43, subjectName = "Second subject «кавычки»", periodKey = "2025-2", url = url,
                    tabGid = 0, tabName = "", rowKey = "Тестов Тест", keyColumn = 1, keyKind = "NAME",
                    formula = direct(0, "", 0), tracked = false, status = "ROW_NOT_FOUND",
                    connectedAt = 1790000500000,
                ),
            )
        )

        assertMigrates("sheet_scores/state-sp08.json", expected)
    }

    @Test fun `an AI rule keeps its computed formula, official total, summary and link`() {
        val node = SheetNode.Apply(
            SheetOp.SUM,
            listOf(
                SheetNode.Cell(SheetCellRef(22, SheetColumnRef("ДЗ 1", 3))),
                SheetNode.Round(SheetNode.Apply(SheetOp.DIVIDE, listOf(SheetNode.Cell(SheetCellRef(11, SheetColumnRef("", 4))), SheetNode.Number(2.0))), 1),
            ),
        )
        val computed = SheetFormula.Computed(node, BlankPolicy.IGNORE_BLANK_INPUTS)
        val ai = connection.copy(
            choice = TotalChoice.AI.name, formula = computed.toStored(), official = direct(22, "Итог", 9),
            officialValue = "0", summary = "Сумма работ", linkId = "00000000-0000-0000-0000-000000000042",
        )
        store.write(StoredSheetScores(connections = listOf(ai)))

        val model = store.read()!!.connections.single().toModel()

        assertEquals(computed, model.formula)
        assertEquals(SheetFormula.Direct(SheetCellRef(22, SheetColumnRef("Итог", 9))), model.official)
        assertEquals("0", model.officialValue)
        assertEquals("Сумма работ", model.summary)
        assertEquals(TotalChoice.AI, model.choice)
        assertEquals(listOf(22L, 11L), model.tabs)
        assertEquals(ai, model.toStored())
    }

    @Test fun `a hand-picked or unsure total keeps how it was chosen, an automatic one writes no field`() {
        val manual = connection.copy(choice = TotalChoice.MANUAL.name)
        store.write(StoredSheetScores(connections = listOf(manual, connection.copy(subjectId = 2, choice = "AUTO_CHECK"))))

        val read = store.read()!!.connections.map { it.toModel().choice }

        assertEquals(listOf(TotalChoice.MANUAL, TotalChoice.AUTO_CHECK), read)
        store.write(StoredSheetScores(connections = listOf(connection.toModel().toStored())))
        assertFalse(file.readText().contains("choice"))
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

        file.writeText("""{"format":3,"connections":[]}""")
        assertThrows(IllegalStateException::class.java) { store.read() }

        file.writeText("not json at all {")
        assertThrows(Exception::class.java) { store.read() }

        val badNode = StoredSheetFormula("computed", StoredSheetNode("power", args = listOf(StoredSheetNode("number", value = 2.0))), "PRESERVE")
        listOf(
            RecordbookStoreJson.encodeToString(StoredSheetScores(connections = listOf(connection))).replace("\"url\":\"https", "\"nourl\":\"https"),
            encode(connection.copy(url = "https://example.org/sheet")),
            encode(connection.copy(rowKey = " ")),
            encode(connection.copy(keyKind = "PHONE")),
            encode(connection.copy(status = "LOST")),
            encode(connection.copy(formula = null)),
            encode(connection.copy(formula = direct(22, "x", -1))),
            encode(connection.copy(formula = badNode)),
            encode(connection.copy(formula = StoredSheetFormula("computed", StoredSheetNode("sum", args = emptyList()), "PRESERVE"))),
            encode(connection.copy(formula = StoredSheetFormula("computed", StoredSheetNode("number", value = 1.0), "MAYBE"))),
            encode(connection.copy(choice = "GUESS")),
            // Format 1 needs its column and has no formula.
            encode(connection, format = 1),
            encode(connection.copy(formula = null), format = 1),
            encode(connection.copy(headerPath = "x", columnIndex = -1, formula = null), format = 1),
        ).forEach { content ->
            file.writeText(content)
            assertThrows(content, Exception::class.java) { store.read() }
        }
    }

    private fun encode(connection: StoredSheetConnection, format: Int = SHEET_SCORES_FORMAT) =
        RecordbookStoreJson.encodeToString(StoredSheetScores(format = format, connections = listOf(connection)))

    private fun assertMigrates(golden: String, expected: StoredSheetScores) {
        copyStored22(golden, directory, "state.json")

        assertEquals(expected, store.read())
        store.write(expected)
        val written = file.readText()
        assertTrue(written, written.startsWith("""{"format":2,"""))
        assertFalse(written, written.contains("columnIndex"))
        assertEquals(expected, SheetScoresFileStore(directory.toOkioPath(), FileSystem.SYSTEM).read())
    }
}
