package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.data.assertSameJson
import dev.alllexey.itmowidgets.feature.schedule.data.copyStored22
import dev.alllexey.itmowidgets.feature.schedule.data.directoriesAt
import dev.alllexey.itmowidgets.feature.schedule.data.stored22
import java.io.File
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The 2.2 `schedule_changes/state.json` reads into the same moments and is written back with every value unchanged. */
class ScheduleChanges22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `the 2_2 state maps to the same moments and is rewritten unchanged`() {
        val file = copyStored22(FIXTURE, File(temporary.root, "files/schedule_changes"))
        val store = ScheduleChangesFileStore(directoriesAt(temporary.root))

        val state = checkNotNull(store.read())
        val change = state.changes.single().toModel()

        assertEquals(Instant.fromEpochMilliseconds(1_791_104_400_000), change.detectedAt)
        assertEquals(ScheduleChangeKind.UPDATED, change.kind)
        assertEquals(setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE), change.fields)
        val before = checkNotNull(change.before)
        val after = checkNotNull(change.after)
        assertEquals(LocalDate(2026, 10, 5), before.date)
        assertEquals(LocalTime(10, 0) to LocalTime(11, 30), before.start to before.end)
        assertEquals(LocalTime(11, 40) to LocalTime(13, 10), after.start to after.end)
        assertEquals(LocalDate(2026, 10, 5), checkNotNull(state.snapshot).toModel().lessons.single().slot().date)

        store.write(state)

        assertSameJson(stored22(FIXTURE).decodeToString(), file.readText())
        assertEquals(state, store.read())
    }

    private companion object {
        const val FIXTURE = "schedule_changes/state.json"
    }
}
