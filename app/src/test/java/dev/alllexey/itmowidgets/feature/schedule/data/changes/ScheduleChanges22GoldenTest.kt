package dev.alllexey.itmowidgets.feature.schedule.data.changes

import api.myitmo.MyItmo
import com.google.gson.JsonParser
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.di.NetworkModule
import java.io.File
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The 2.2 `schedule_changes/state.json` reads into the kotlinx model and is written back with every value unchanged.
 * Gson orders keys by the runtime's field order (ART: by name, as captured; the JVM: as declared), so the JVM compares
 * JSON trees, where every string, number and key must still match exactly.
 */
class ScheduleChanges22GoldenTest {
    @get:Rule val temporary = TemporaryFolder()

    private val gson = NetworkModule.provideGson(NetworkModule.provideWidgetsClient(MyItmo(), "https://localhost/"))

    @Test
    fun `the 2_2 state maps to the same moments and is rewritten unchanged`() {
        val original = File(FIXTURE).readText(Charsets.UTF_8)
        val directory = File(temporary.root, "schedule_changes").apply { mkdirs() }
        File(directory, "state.json").writeText(original, Charsets.UTF_8)
        val store = ScheduleChangesFileStore(directory, gson)

        val state = checkNotNull(store.read())
        val change = state.changes.single().toModel()

        assertEquals(Instant.fromEpochMilliseconds(1_791_104_400_000), change.detectedAt)
        assertEquals(ScheduleChangeKind.UPDATED, change.kind)
        assertEquals(setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE), change.fields)
        assertEquals(LocalDate(2026, 10, 5), checkNotNull(change.before).date)
        assertEquals(LocalTime(10, 0) to LocalTime(11, 30), change.before!!.start to change.before!!.end)
        assertEquals(LocalTime(11, 40) to LocalTime(13, 10), change.after!!.start to change.after!!.end)
        assertEquals(LocalDate(2026, 10, 5), checkNotNull(state.snapshot).toModel().lessons.single().slot().date)

        store.write(state)

        val rewritten = File(directory, "state.json").readText(Charsets.UTF_8)
        assertEquals(JsonParser.parseString(original), JsonParser.parseString(rewritten))
    }

    private companion object {
        const val FIXTURE = "src/androidTest/assets/upgrade-2.2/files/schedule_changes/state.json"
    }
}
