package dev.alllexey.itmowidgets.feature.map.data.rooms

import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The mirror's rooms file: the label points of a plan's rooms and amenities, every value a string as in the tiles. */
object RoomsJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(buildingId: Int, text: String): List<MapRoom> =
        json.decodeFromString<List<Unit>>(text).mapNotNull { it.toRoom(buildingId) }

    @Serializable
    private class Unit(
        val id: String,
        val level: String? = null,
        @SerialName("class") val category: String? = null,
        val subclass: String? = null,
        val name: String? = null,
        val ref: String? = null,
        @SerialName("isu:room_id") val isuRoomId: String? = null,
        val lngLat: List<Double>,
    )

    private fun Unit.toRoom(buildingId: Int): MapRoom? = MapRoom(
        id = id.toLongOrNull() ?: return null,
        buildingId = buildingId,
        level = level?.toIntOrNull() ?: 0,
        category = category ?: return null,
        kind = subclass,
        name = name,
        number = ref,
        isuRoomId = isuRoomId?.toLongOrNull(),
        longitude = lngLat[0],
        latitude = lngLat[1],
    )
}
