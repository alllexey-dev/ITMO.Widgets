package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings

/** How a room is named on the map: its number, else its name, else what it is («Туалеты»), else «Помещение». */
internal object RoomTexts {
    fun title(room: MapRoom): UiText = room.label()?.let(UiText::Dynamic)
        ?: Place.of(room)?.text
        ?: UiText.Res(MapStrings.map_room_fallback)

    /** The map sprite of the room's kind, as itmo-map-web picks it; `default` for a kind without one. */
    fun icon(room: MapRoom): String = SPRITES[room.kind] ?: Place.of(room)?.icon ?: "default"

    fun floor(level: Int): UiText = UiText.Res(MapStrings.map_floor, listOf(level + 1))

    /** «1 этаж, Lemon Classroom»: the floor and the name when the title is the number. */
    fun subtitle(room: MapRoom, building: String? = null): UiText {
        val name = room.name?.trim()?.takeIf { it.isNotEmpty() && room.number != null && !sameText(it, room.number) }
        val parts = listOfNotNull(floor(room.level), name?.let(UiText::Dynamic), building?.let(UiText::Dynamic))
        return UiText.Joined(parts, ", ")
    }

    /** The plan's number without the `(присв)`/`(усл)` marks, else its name. */
    fun MapRoom.label(): String? = number?.replace(MARK, "")?.trim()?.takeIf(String::isNotEmpty)
        ?: name?.trim()?.takeIf(String::isNotEmpty)

    private fun sameText(a: String, b: String) = a.replace(MARK, "").trim().equals(b.replace(MARK, "").trim(), ignoreCase = true)

    private val SPRITES = mapOf(
        "education" to "auditory", "office" to "office", "meetingroom" to "meetingroom", "conferenceroom" to "conferenceroom",
        "museum" to "museum", "fitnessroom" to "gym", "lockerroom.female" to "lockerroom.female",
        "lockerroom.male" to "lockerroom.male", "stairs" to "stairs", "restroom.male" to "amenity.restroom.male",
        "restroom.female" to "amenity.restroom.female", "first_aid_kit" to "healthcare", "bicycle_parking" to "parking.bike",
    )

    private val MARK = Regex("""\s*\((присв|усл)\)\s*$""")
}
