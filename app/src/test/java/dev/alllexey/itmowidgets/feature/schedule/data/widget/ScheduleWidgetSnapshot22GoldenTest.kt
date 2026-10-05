package dev.alllexey.itmowidgets.feature.schedule.data.widget

import api.myitmo.MyItmo
import com.google.gson.JsonParser
import dev.alllexey.itmowidgets.di.NetworkModule
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 2.2 `widgets/schedule_snapshot.json` reads and is written back with every value unchanged by the store's Gson.
 * Key order follows the runtime's field order (ART: by name, as captured; the JVM: as declared), so the JVM compares
 * JSON trees.
 */
class ScheduleWidgetSnapshot22GoldenTest {
    private val gson = NetworkModule.provideGson(NetworkModule.provideWidgetsClient(MyItmo(), "https://localhost/"))

    @Test
    fun `the 2_2 snapshot is rewritten unchanged`() {
        val original = File(FIXTURE).readText(Charsets.UTF_8)

        val snapshot = gson.fromJson(original, ScheduleWidgetSnapshot::class.java)

        assertEquals(JsonParser.parseString(original), JsonParser.parseString(gson.toJson(snapshot)))
    }

    private companion object {
        const val FIXTURE = "src/androidTest/assets/upgrade-2.2/no_backup/widgets/schedule_snapshot.json"
    }
}
