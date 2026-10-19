package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings

/** The place chips of the search, by the plan's `subclass` (the set of itmo-map-web), with its chip [icon] sprite. */
enum class Place(val label: String, val icon: String, val kinds: Set<String>) {
    RESTROOMS(MapStrings.map_place_restrooms, "amenity.restroom.unisex", setOf("restroom", "restroom.unisex", "restroom.male", "restroom.female")),
    FOOD(MapStrings.map_place_food, "canteen", setOf("canteen", "food")),
    COWORKING(MapStrings.map_place_coworking, "coworking", setOf("coworking")),
    CLOAKROOM(MapStrings.map_place_cloakroom, "cloakroom", setOf("cloakroom")),
    STUDENTS(MapStrings.map_place_students, "studoffice", setOf("studentservices")),
    LIBRARY(MapStrings.map_place_library, "library", setOf("library")),
    HEALTH(MapStrings.map_place_health, "healthcare", setOf("healthcare")),
    ENTRANCES(MapStrings.map_place_entrances, "entrance", setOf("entrance")),
    WATER(MapStrings.map_place_water, "amenity.water", setOf("drinking_water")),
    ATM(MapStrings.map_place_atm, "cash", setOf("atm"));

    fun matches(room: MapRoom): Boolean = room.kind in kinds

    /** The room's name when it has none of its own: «Туалеты» for a restroom. */
    val text: UiText get() = UiText.Res(label)

    companion object {
        fun of(room: MapRoom): Place? = entries.firstOrNull { it.matches(room) }
    }
}
