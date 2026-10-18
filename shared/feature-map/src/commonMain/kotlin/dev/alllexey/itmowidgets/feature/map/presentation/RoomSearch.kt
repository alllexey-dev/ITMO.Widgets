package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.feature.map.presentation.RoomTexts.label

/**
 * The map's search, as the campus app's (data.md "Поиск") but over what people type: the number or name starting
 * with the query first, then containing it; places by kind are the chips. Corridors and stairs are never results.
 */
internal object RoomSearch {
    fun search(query: String, rooms: List<MapRoom>): List<MapRoom> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val candidates = rooms.filter { it.category !in HIDDEN }
        val keys = candidates.associateWith { room -> listOfNotNull(room.label(), room.name, room.number).map(::normalize) }
        val starts = candidates.filter { room -> keys.getValue(room).any { it.startsWith(q) } }
        val contains = candidates.filter { room -> room !in starts && keys.getValue(room).any { q in it } }
        return sorted(starts) + sorted(contains)
    }

    fun ofPlace(place: Place, rooms: List<MapRoom>): List<MapRoom> = sorted(rooms.filter(place::matches))

    private fun sorted(rooms: List<MapRoom>) = rooms.sortedWith(compareBy({ it.level }, { it.label().orEmpty() }))

    private fun normalize(text: String) = text.lowercase().replace('ё', 'е').filterNot(Char::isWhitespace)

    private val HIDDEN = setOf("corridor", "stairs")
}
