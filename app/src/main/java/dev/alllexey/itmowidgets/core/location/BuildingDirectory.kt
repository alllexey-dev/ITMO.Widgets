package dev.alllexey.itmowidgets.core.location

import com.google.gson.Gson

/**
 * Curated ITMO buildings for the map hand-off.
 *
 * Lookup is by MyITMO building id first, then by a lower-case alias inside the
 * building name; unknown buildings resolve to nothing so callers fall back to
 * the raw address text.
 */
class BuildingDirectory(private val buildings: List<KnownBuilding>) {

    init {
        require(buildings.map { it.id }.toSet().size == buildings.size) { "Duplicate building ids" }
    }

    val all: List<KnownBuilding> get() = buildings

    fun find(buildingId: Int?, mainBuildingId: Int?, buildingName: String?): KnownBuilding? {
        listOfNotNull(mainBuildingId, buildingId).forEach { id ->
            buildings.firstOrNull { id in it.buildingIds }?.let { return it }
        }
        val name = buildingName?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return buildings.firstOrNull { building -> building.aliases.any { alias -> alias in name } }
    }

    companion object {
        fun parse(json: String, gson: Gson): BuildingDirectory =
            BuildingDirectory(gson.fromJson(json, Array<KnownBuilding>::class.java).toList())
    }
}
