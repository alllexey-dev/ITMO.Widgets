package dev.alllexey.itmowidgets.core.location

import kotlinx.serialization.Serializable

/**
 * One ITMO building from `res/raw/itmo_buildings.json`; coordinates checked against OpenStreetMap on 2026-09-20.
 * [planBuildingId] is the building's id in ITMO's campus map (docs/features/map.md), null without a floor plan.
 */
@Serializable
data class KnownBuilding(
    val id: String,
    val buildingIds: List<Int>,
    val aliases: List<String>,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val planBuildingId: Int? = null,
) {
    fun toMapDestination(): MapDestination = MapDestination(label, address, latitude, longitude)
}
