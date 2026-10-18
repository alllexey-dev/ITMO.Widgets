package dev.alllexey.itmowidgets.feature.map.domain.catalog

import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.feature.map.domain.trip.BuildingPlan

/** The buildings the mirror has plans for, the [attribution] the map shows and the [glyphsUrl] template of its labels. */
data class MapCatalog(val buildings: List<MapBuilding>, val attribution: String, val glyphsUrl: String) {
    val ids: Set<Int> get() = buildings.mapTo(HashSet()) { it.id }

    fun building(id: Int): MapBuilding? = buildings.firstOrNull { it.id == id }
}

/** A building with a plan: its [levels] from the lowest, the camera's [bounds] (west, south, east, north) and [bearing]. */
data class MapBuilding(
    val id: Int,
    val name: String,
    val bounds: List<Double>,
    val center: MapPoint,
    val bearing: Double,
    val levels: List<Int>,
)

data class MapPoint(val longitude: Double, val latitude: Double)

/** A building's plan on the device: the [tilesPath] of its MBTiles file and the decoded rooms and graph. */
data class LoadedPlan(val building: MapBuilding, val tilesPath: String, val plan: BuildingPlan) {
    val rooms: List<MapRoom> get() = plan.rooms
}
