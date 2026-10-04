package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksFileStore
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredBarsPlan
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredBarsSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredCheckpointMark
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredMarkNews
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredMarks
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredMyItmoSnapshot
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.StoredMyItmoSubject
import dev.alllexey.itmowidgets.upgrade.Captured22.AT_MS
import dev.alllexey.itmowidgets.upgrade.Captured22.HALF
import dev.alllexey.itmowidgets.upgrade.Captured22.ISU
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import org.junit.Assert.assertEquals

/** `files/marks/state.json` (format 1): both snapshots and the unread subjects. */
object MarksFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val expected = StoredMarks(
            owner = ISU,
            myItmo = StoredMyItmoSnapshot(
                half = HALF,
                fetchedAt = AT_MS,
                subjects = listOf(StoredMyItmoSubject(9001, 3, 9101, 1001, SUBJECT, 87.5, "отлично"))
            ),
            bars = StoredBarsSnapshot(
                half = HALF,
                fetchedAt = AT_MS,
                plans = listOf(
                    StoredBarsPlan(
                        9201, "exam", "PLAN-9201", SUBJECT, 42.0, null, 1, false,
                        listOf(StoredCheckpointMark(9301, 10.0, false), StoredCheckpointMark(9302, null, true))
                    )
                )
            ),
            news = listOf(StoredMarkNews("news-9001", HALF, "тестовая дисциплина", SUBJECT, AT_MS, true))
        )

        assertEquals(expected, MarksFileStore(File(fixture.filesDir, "marks"), fixture.gson).read())
    }
}
