package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/**
 * The WidgetKit reference fixture (L18 IO-10b) is what this build writes for [ScheduleWidgetTimelineSamples.fixtureTimeline]
 * and reads back unchanged. A deliberate change to the timeline regenerates it with `ITMO_WRITE_TIMELINE_FIXTURE=1`.
 */
class ScheduleWidgetTimelineFixtureTest {

    // Host tests run in the module directory.
    private val fixture = File("fixtures/schedule-widget-timeline-v1.json")

    @Test
    fun theFixtureIsWhatTheSelectorWritesAndRoundTrips() {
        val timeline = ScheduleWidgetTimelineSamples.fixtureTimeline()
        val encoded = ScheduleWidgetTimelineJson.encode(timeline)
        if (System.getenv("ITMO_WRITE_TIMELINE_FIXTURE") == "1") fixture.writeText(pretty(encoded))

        val text = fixture.readText()
        val decoded = ScheduleWidgetTimelineJson.decode(text)

        assertEquals(timeline, decoded)
        assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(decoded)))
    }

    private fun pretty(text: String): String {
        val printer = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        return printer.encodeToString(Json.parseToJsonElement(text)) + "\n"
    }
}
