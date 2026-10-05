package dev.alllexey.itmowidgets.core.location

/** One ITMO building from `res/raw/itmo_buildings.json`; coordinates checked against OpenStreetMap on 2026-09-20. */
data class KnownBuilding(
    val id: String,
    val buildingIds: List<Int>,
    val aliases: List<String>,
    val label: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) {
    fun toMapDestination(): MapDestination = MapDestination(label, address, latitude, longitude)
}
