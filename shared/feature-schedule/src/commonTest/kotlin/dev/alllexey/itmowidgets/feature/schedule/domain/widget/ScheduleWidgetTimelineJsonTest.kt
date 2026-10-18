package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.WidgetColorRoles
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ScheduleWidgetTimelineJsonTest {

    private val timeline = ScheduleWidgetTimelineSamples.fixtureTimeline()

    @Test
    fun aTimelineRoundTrips() {
        assertEquals(timeline, ScheduleWidgetTimelineJson.decode(ScheduleWidgetTimelineJson.encode(timeline)))
    }

    @Test
    fun versionAndDefaultsAreWrittenAndNullsAreNot() {
        val text = ScheduleWidgetTimelineJson.encode(timeline)
        val root = Json.parseToJsonElement(text).jsonObject
        val first = root.getValue("entries").jsonArray.first().jsonObject.getValue("snapshot").jsonObject

        assertEquals(JsonPrimitive(2), root["version"])
        assertTrue("window" in first.getValue("next").jsonObject)
        assertTrue("gaps" in first.getValue("day").jsonObject)
        assertFalse(text.contains("null"))
    }

    @Test
    fun anotherOrAMissingVersionIsRejected() {
        val root = Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(timeline)).jsonObject

        listOf(1, 3).forEach { version ->
            assertFailsWith<IllegalArgumentException> {
                ScheduleWidgetTimelineJson.decode(JsonObject(root + ("version" to JsonPrimitive(version))).toString())
            }
        }
        assertFailsWith<IllegalArgumentException> { ScheduleWidgetTimelineJson.decode(JsonObject(root - "version").toString()) }
    }

    @Test
    fun thePaletteRoundTrips() {
        val roles = WidgetColorRoles(0xFFFFFF, 0xF0F0F0, 0x111111, 0x444444, 0xCCCCCC, 0x009688)
        val themed = timeline.copy(palette = WidgetPalette(light = roles, dark = roles.copy(surface = 0x101010)))

        assertEquals(themed, ScheduleWidgetTimelineJson.decode(ScheduleWidgetTimelineJson.encode(themed)))
    }

    @Test
    fun anAdditiveFieldKeepsTheVersionReadable() {
        val root = Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(timeline)).jsonObject
        val extended = JsonObject(root + ("addedLater" to JsonPrimitive(true))).toString()

        assertEquals(timeline, ScheduleWidgetTimelineJson.decode(extended))
    }
}
