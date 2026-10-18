package dev.alllexey.itmowidgets.feature.sport.domain.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

/**
 * The WidgetKit reference fixture of the sport widget (`iosApp/Tests/UnitTests/SportTimelineTests.swift`): the sample's
 * queue from the Monday on, then the credit reached from the Tuesday, read back unchanged. A deliberate change to the
 * timeline regenerates it with `ITMO_WRITE_TIMELINE_FIXTURE=1`.
 */
class SportWidgetTimelineFixtureTest {

    // Host tests run in the module directory.
    private val fixture = File("fixtures/sport-widget-timeline-v1.json")

    @Test
    fun theFixtureIsTheSampleTimelineAndRoundTrips() {
        val encoded = SportWidgetTimelineJson.encode(TIMELINE)
        if (System.getenv("ITMO_WRITE_TIMELINE_FIXTURE") == "1") {
            fixture.parentFile.mkdirs()
            fixture.writeText(pretty(encoded))
        }

        val text = fixture.readText()
        val decoded = SportWidgetTimelineJson.decode(text)

        assertEquals(TIMELINE, decoded)
        assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(SportWidgetTimelineJson.encode(decoded)))
    }

    private fun pretty(text: String): String {
        val printer = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        return printer.encodeToString(Json.parseToJsonElement(text)) + "\n"
    }

    private companion object {
        val LABELS = SportWidgetSampleLabels(swimming = "Плавание", volleyball = "Волейбол")
        val TIMELINE = SportWidgetTimeline(
            generatedAt = "2026-09-07T09:22:00Z",
            validUntil = "2026-09-13T21:00:00Z",
            entries = listOf(
                SportWidgetTimelineEntry("2026-09-07T09:22:00Z", SportWidgetSamples.snapshot(SportWidgetSampleState.QUEUE, LABELS)),
                SportWidgetTimelineEntry("2026-09-07T21:00:00Z", SportWidgetSamples.snapshot(SportWidgetSampleState.DONE, LABELS)),
            ),
        )
    }
}
