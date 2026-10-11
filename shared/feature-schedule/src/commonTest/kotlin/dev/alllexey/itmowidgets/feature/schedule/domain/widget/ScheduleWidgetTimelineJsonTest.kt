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
import kotlinx.serialization.json.jsonPrimitive

class ScheduleWidgetTimelineJsonTest {

    private val timeline = ScheduleWidgetTimelineSamples.fixtureTimeline()

    @Test
    fun aTimelineRoundTrips() {
        assertEquals(timeline, ScheduleWidgetTimelineJson.decode(ScheduleWidgetTimelineJson.encode(timeline)))
    }

    @Test
    fun versionAndDefaultsAreWrittenAndNullsAreNot() {
        val root = Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(timeline)).jsonObject
        val first = root.getValue("entries").jsonArray.first().jsonObject.getValue("snapshot").jsonObject

        assertEquals(JsonPrimitive(1), root["version"])
        assertEquals("2026-08-10T07:00:30.250Z", root.getValue("generatedAt").jsonPrimitive.content)
        assertTrue("remainingLessons" in first.getValue("singleLesson").jsonObject)
        assertTrue(first.getValue("lessonList").jsonArray.all { "tomorrow" in it.jsonObject })
        assertFalse("officialFallback" in root.getValue("entries").jsonArray.last().jsonObject
            .getValue("snapshot").jsonObject)
        assertFalse(ScheduleWidgetTimelineJson.encode(timeline).contains("null"))
    }

    @Test
    fun aHigherOrMissingVersionIsRejected() {
        val root = Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(timeline)).jsonObject

        assertFailsWith<IllegalArgumentException> {
            ScheduleWidgetTimelineJson.decode(JsonObject(root + ("version" to JsonPrimitive(2))).toString())
        }
        assertFailsWith<IllegalArgumentException> {
            ScheduleWidgetTimelineJson.decode(JsonObject(root - "version").toString())
        }
    }

    @Test
    fun thePaletteIsAbsentWhileTheWidgetsKeepTheirColoursAndRoundTripsInVersion1() {
        assertFalse("palette" in Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(timeline)).jsonObject)

        val roles = WidgetColorRoles(0xFFFFFF, 0xF0F0F0, 0x111111, 0x444444, 0xCCCCCC, 0x009688)
        val themed = timeline.copy(palette = WidgetPalette(light = roles, dark = roles.copy(surface = 0x101010)))
        val root = Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(themed)).jsonObject

        assertEquals(JsonPrimitive(1), root["version"])
        assertEquals(JsonPrimitive(0x009688), root.getValue("palette").jsonObject.getValue("light").jsonObject["primary"])
        assertEquals(themed, ScheduleWidgetTimelineJson.decode(root.toString()))
    }

    @Test
    fun anAdditiveFieldKeepsTheVersionReadable() {
        val root = Json.parseToJsonElement(ScheduleWidgetTimelineJson.encode(timeline)).jsonObject
        val extended = JsonObject(root + ("addedLater" to JsonPrimitive(true))).toString()

        assertEquals(timeline, ScheduleWidgetTimelineJson.decode(extended))
    }
}
