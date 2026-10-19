package dev.alllexey.itmowidgets.feature.map.domain.rooms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomMatcherTest {

    @Test
    fun timetableRoomsOfTheOwnersYearFindTheirPlanRooms() {
        val cases = mapOf(
            "2403" to 71L,
            "2306/1" to 627L,
            "Lemon Classroom (1419)" to 262L,
            "Актовый зал (1216)" to 50L,
            "Актовый зал (1216/0 (усл))" to 50L,
            "Медиазона (1303/8)" to 134L,
            "0001" to 1L,
        )
        cases.forEach { (timetable, plan) ->
            val match = RoomMatcher.match(timetable, ROOMS)
            assertEquals(plan, match?.room?.id, timetable)
            assertEquals(false, match?.approximate, timetable)
        }
    }

    @Test
    fun aLetteredRoomMissingOnThePlanFallsBackToItsNumber() {
        val match = RoomMatcher.match("1224в", ROOMS)

        assertEquals(42L, match?.room?.id)
        assertTrue(match!!.approximate)
    }

    @Test
    fun aLetteredRoomOnThePlanIsExact() {
        assertEquals(RoomMatch(room(43, "1003а"), approximate = false), RoomMatcher.match("1003А", ROOMS))
    }

    @Test
    fun aCompositeRoomIsNotTheRoomOfItsNumber() {
        assertEquals(1472L, RoomMatcher.match("201 (составное)", ROOMS)?.room?.id)
        assertEquals(700L, RoomMatcher.match("201", ROOMS)?.room?.id)
    }

    @Test
    fun corridorsAndUnknownRoomsDoNotMatch() {
        assertNull(RoomMatcher.match("1 этаж", ROOMS))
        assertNull(RoomMatcher.match("9999", ROOMS))
        assertNull(RoomMatcher.match("№02", ROOMS))
        assertNull(RoomMatcher.match("  ", ROOMS))
    }

    private companion object {
        fun room(id: Long, number: String?, name: String? = number, category: String = "room") =
            MapRoom(id, 13, 0, category, "education", name, number, null, 30.0, 60.0)

        val ROOMS = listOf(
            room(1, "0001 (присв)", "0001"),
            room(71, "2403"),
            room(627, null, "2306/1"),
            room(488, "2306/2"),
            room(262, "1419", "Lemon Classroom"),
            room(50, "1216", "Актовый зал"),
            room(134, "1303/8", "Медиазона"),
            room(42, "1224"),
            room(43, "1003а"),
            room(44, "1003"),
            room(700, "201"),
            room(1472, "201 (составное)"),
            room(123, null, "1 этаж", category = "corridor"),
        )
    }
}
