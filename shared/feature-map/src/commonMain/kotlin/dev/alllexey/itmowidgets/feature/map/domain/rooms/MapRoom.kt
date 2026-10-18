package dev.alllexey.itmowidgets.feature.map.domain.rooms

/**
 * A room or point of a building plan: [id] is the plan's id (the graph's `area_id`), [level] the floor (`0` is the
 * first), [number] the room number (`ref`), [isuRoomId] its ISU id, [category] ITMO's `class` (`room`, `corridor`,
 * `stairs`, `poi`, ...) and [kind] its `subclass` (`education`, `restroom.male`, `entrance`, ...).
 */
data class MapRoom(
    val id: Long,
    val buildingId: Int,
    val level: Int,
    val category: String,
    val kind: String?,
    val name: String?,
    val number: String?,
    val isuRoomId: Long?,
    val longitude: Double,
    val latitude: Double,
) {
    val isEntrance: Boolean get() = kind == ENTRANCE

    private companion object {
        const val ENTRANCE = "entrance"
    }
}
