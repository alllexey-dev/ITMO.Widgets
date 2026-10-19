package dev.alllexey.itmowidgets.feature.map.domain.rooms

import dev.alllexey.itmowidgets.feature.map.data.rooms.RoomsJson
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * RoomMatcher against itmo-map-web's `rooms.js` (`scripts/map/room-fixtures.mjs`) on every timetable room of
 * Backend's synced lessons: the Kotlin match is one of the rooms the reference accepts, or none when it finds none.
 * Runs when `CAMPUS_MAP_ROOMS` (a directory of `<id>.json`, the one the cases were generated from) is set, since the
 * plans are not committed here.
 */
class RoomMatcherParityTest {

    @Test
    fun `timetable rooms match rooms js`() {
        val rooms = System.getenv("CAMPUS_MAP_ROOMS")
        assumeTrue("CAMPUS_MAP_ROOMS is not set", rooms != null)
        val cases = Json.decodeFromString<List<Case>>(resource("timetable-rooms.json"))
        assertTrue("no cases", cases.isNotEmpty())
        val plans = cases.map { it.building }.distinct().associateWith { RoomsJson.decode(it, File(rooms, "$it.json").readText()) }

        val mismatches = cases.mapNotNull { case ->
            val match = RoomMatcher.match(case.room, plans.getValue(case.building))
            val agrees = if (case.ids.isEmpty()) match == null else match?.room?.id in case.ids && match?.approximate == case.approximate
            "${case.building} ${case.room}: expected ${case.ids} approximate=${case.approximate}, got $match".takeUnless { agrees }
        }
        assertEquals("", mismatches.joinToString("\n"))
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("rooms/$name")) { "rooms/$name" }.reader().readText()

    @Serializable
    private data class Case(val building: Int, val room: String, val ids: List<Long>, val approximate: Boolean)
}
