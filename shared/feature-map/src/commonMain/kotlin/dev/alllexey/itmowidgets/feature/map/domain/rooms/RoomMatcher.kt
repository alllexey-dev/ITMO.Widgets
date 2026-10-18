package dev.alllexey.itmowidgets.feature.map.domain.rooms

/** A timetable room found on a plan; [approximate] when only the number without its letter matched (`1224в` -> `1224`). */
data class RoomMatch(val room: MapRoom, val approximate: Boolean)

/**
 * Finds a My ITMO timetable room (`2403`, `2306/1`, `Lemon Classroom (1419)`, `Актовый зал (1216/0 (усл))`) among a
 * building's plan rooms (docs/features/map.md "Room mapping"). Keys are the whole text and, for `Name (number)`,
 * the number and the name; each is compared with the plan's room number, then with its name, ignoring case, `ё`,
 * spaces and the `(присв)`/`(усл)` marks.
 */
object RoomMatcher {
    private val PARENTHESIS = Regex("""\((.*)\)\s*$""")
    private val MARK = Regex("""\s*\((присв|усл)\)\s*$""")
    private val TRAILING_LETTER = Regex("""^(.*\d)\p{L}$""")

    fun match(timetableRoom: String, rooms: List<MapRoom>): RoomMatch? {
        val candidates = rooms.filter { it.category == ROOM }
        val keys = keys(timetableRoom)
        find(keys, candidates)?.let { return RoomMatch(it, approximate = false) }
        val shorter = keys.mapNotNull { TRAILING_LETTER.find(it)?.groupValues?.get(1) }
        return find(shorter, candidates)?.let { RoomMatch(it, approximate = true) }
    }

    private fun find(keys: List<String>, rooms: List<MapRoom>): MapRoom? {
        for (key in keys) {
            rooms.firstOrNull { it.number?.let(::normalize) == key }?.let { return it }
            rooms.firstOrNull { it.name?.let(::normalize) == key }?.let { return it }
        }
        return null
    }

    private fun keys(room: String): List<String> {
        val inside = PARENTHESIS.find(room) ?: return listOf(normalize(room)).filter(String::isNotEmpty)
        return listOf(inside.groupValues[1], room.substring(0, inside.range.first)).map(::normalize).filter(String::isNotEmpty)
    }

    private fun normalize(text: String): String =
        text.lowercase().replace('ё', 'е').trim().replace(MARK, "").filterNot(Char::isWhitespace)

    private const val ROOM = "room"
}
