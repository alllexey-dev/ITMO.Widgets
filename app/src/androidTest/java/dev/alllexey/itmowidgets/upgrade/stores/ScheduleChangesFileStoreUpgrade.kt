package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesFileStore
import dev.alllexey.itmowidgets.feature.schedule.data.changes.StoredChange
import dev.alllexey.itmowidgets.feature.schedule.data.changes.StoredLesson
import dev.alllexey.itmowidgets.feature.schedule.data.changes.StoredScheduleChanges
import dev.alllexey.itmowidgets.feature.schedule.data.changes.StoredSnapshot
import dev.alllexey.itmowidgets.upgrade.Captured22.AT_MS
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT
import dev.alllexey.itmowidgets.upgrade.Captured22.SUBJECT_ID
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import org.junit.Assert.assertEquals

/** `files/schedule_changes/state.json` (format 1): the snapshot and the found change. */
object ScheduleChangesFileStoreUpgrade {

    fun check(fixture: Upgrade22Fixture) {
        val before = StoredLesson(
            pairId = 5001,
            date = "2026-10-05",
            start = "10:00",
            end = "11:30",
            subjectId = SUBJECT_ID,
            subjectName = SUBJECT,
            typeId = 1,
            flowId = 3001,
            flowName = "Тестовый поток",
            teacherIsu = 100101,
            teacherName = "Тестовый преподаватель",
            room = "101",
            building = "Тестовый корпус",
            formatId = 1,
            format = "Очный"
        )
        val expected = StoredScheduleChanges(
            snapshot = StoredSnapshot("2026-10-04", "2026-10-11", listOf(before)),
            emptyHeld = true,
            changes = listOf(
                StoredChange(
                    id = "change-5001",
                    detectedAt = AT_MS,
                    kind = "UPDATED",
                    fields = listOf("TIME", "PLACE"),
                    subjectName = SUBJECT,
                    typeId = 1,
                    flowName = "Тестовый поток",
                    before = before,
                    after = before.copy(start = "11:40", end = "13:10", room = "202"),
                    read = false,
                    notified = true
                )
            )
        )

        val store = ScheduleChangesFileStore(File(fixture.filesDir, "schedule_changes"), fixture.gson)
        assertEquals(expected, store.read())
    }
}
