package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresFileStore
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.StoredSheetConnection
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.StoredSheetScores
import dev.alllexey.itmowidgets.upgrade.Captured22.AT_MS
import dev.alllexey.itmowidgets.upgrade.Captured22.DAY_MS
import dev.alllexey.itmowidgets.upgrade.Captured22.ISU
import dev.alllexey.itmowidgets.upgrade.Captured22.PERIOD
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT_ID
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * `files/sheet_scores/state.json` (format 1): the connected sheet and its last reading. Gson wrote it; kotlinx reads it
 * and writes format 1 back.
 */
object SheetScoresFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val expected = StoredSheetScores(
            owner = ISU,
            connections = listOf(
                StoredSheetConnection(
                    subjectId = SUBJECT_ID,
                    subjectName = SUBJECT,
                    periodKey = PERIOD,
                    url = "https://docs.google.com/spreadsheets/d/upgrade22SyntheticSheetId0001/edit#gid=0",
                    tabGid = 0,
                    tabName = "Баллы",
                    rowKey = ISU.toString(),
                    keyColumn = 0,
                    keyKind = "ISU",
                    headerPath = "Итого",
                    columnIndex = 5,
                    value = "71",
                    baseline = "65",
                    tracked = true,
                    status = "OK",
                    updatedAt = AT_MS,
                    connectedAt = AT_MS - DAY_MS
                )
            )
        )

        val store = SheetScoresFileStore(AndroidAppDirectories(fixture.context))
        assertEquals(expected, store.read())
        store.write(expected)
        val written = File(fixture.filesDir, "sheet_scores/state.json").readText()
        assertTrue(written, written.startsWith("{\"format\":1,"))
        assertEquals(expected, SheetScoresFileStore(AndroidAppDirectories(fixture.context)).read())
    }
}
