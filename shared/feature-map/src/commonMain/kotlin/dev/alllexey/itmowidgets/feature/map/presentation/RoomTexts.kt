package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings

/** How a room is named on the map: its number, else its name, else what it is («Туалет»), else «Помещение». */
internal object RoomTexts {
    fun title(room: MapRoom): UiText = room.label()?.let(UiText::Dynamic)
        ?: kind(room)
        ?: UiText.Res(MapStrings.map_room_fallback)

    /** What the room is («Аудитория», «Столовая»), as itmo-map-web names the plan's subclass; null for corridors and the like. */
    fun kind(room: MapRoom): UiText? = KINDS[room.kind]?.let(UiText::Res)

    /** The map sprite of the room's kind, as itmo-map-web picks it; `default` for a kind without one. */
    fun icon(room: MapRoom): String = SPRITES[room.kind] ?: Place.of(room)?.icon ?: "default"

    fun floor(level: Int): UiText = UiText.Res(MapStrings.map_floor, listOf(level + 1))

    /**
     * «Аудитория · 4 этаж», «Lemon Classroom · 4 этаж» as itmo-map-web: under a number, the room's own name, else what
     * it is; then the floor and the [building] when given.
     */
    fun subtitle(room: MapRoom, building: String? = null): UiText {
        val name = room.name?.trim()?.takeIf { it.isNotEmpty() && room.number != null && !sameText(it, room.number) }
        // A name of its own says what the room is («Столовая», «Lemon Classroom»): the kind only for a bare number.
        val kind = kind(room)?.takeIf { room.number != null && name == null }
        val parts = listOfNotNull(kind, name?.let(UiText::Dynamic), floor(room.level), building?.let(UiText::Dynamic))
        return UiText.Joined(parts, " · ")
    }

    /** The plan's number without the `(присв)`/`(усл)` marks, else its name. */
    fun MapRoom.label(): String? = number?.replace(MARK, "")?.trim()?.takeIf(String::isNotEmpty)
        ?: name?.trim()?.takeIf(String::isNotEmpty)

    private fun sameText(a: String, b: String) = a.replace(MARK, "").trim().equals(b.replace(MARK, "").trim(), ignoreCase = true)

    private val KINDS = mapOf(
        "education" to MapStrings.map_kind_education, "office" to MapStrings.map_kind_office,
        "meetingroom" to MapStrings.map_kind_meetingroom, "conferenceroom" to MapStrings.map_kind_conferenceroom,
        "coworking" to MapStrings.map_kind_coworking, "library" to MapStrings.map_kind_library,
        "canteen" to MapStrings.map_kind_canteen, "food" to MapStrings.map_kind_food, "cloakroom" to MapStrings.map_kind_cloakroom,
        "healthcare" to MapStrings.map_kind_healthcare, "first_aid_kit" to MapStrings.map_kind_healthcare,
        "studentservices" to MapStrings.map_kind_studentservices, "museum" to MapStrings.map_kind_museum,
        "fitnessroom" to MapStrings.map_kind_fitnessroom, "lockerroom.female" to MapStrings.map_kind_lockerroom,
        "lockerroom.male" to MapStrings.map_kind_lockerroom, "entrance" to MapStrings.map_kind_entrance,
        "restroom" to MapStrings.map_kind_restroom, "restroom.unisex" to MapStrings.map_kind_restroom,
        "restroom.male" to MapStrings.map_kind_restroom, "restroom.female" to MapStrings.map_kind_restroom,
        "atm" to MapStrings.map_kind_atm, "drinking_water" to MapStrings.map_kind_water, "bicycle_parking" to MapStrings.map_kind_bike,
    )

    private val SPRITES = mapOf(
        "education" to "auditory", "office" to "office", "meetingroom" to "meetingroom", "conferenceroom" to "conferenceroom",
        "museum" to "museum", "fitnessroom" to "gym", "lockerroom.female" to "lockerroom.female",
        "lockerroom.male" to "lockerroom.male", "stairs" to "stairs", "restroom.male" to "amenity.restroom.male",
        "restroom.female" to "amenity.restroom.female", "first_aid_kit" to "healthcare", "bicycle_parking" to "parking.bike",
    )

    private val MARK = Regex("""\s*\((присв|усл)\)\s*$""")
}
