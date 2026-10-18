package dev.alllexey.itmowidgets.feature.map.domain.rooms

import dev.alllexey.itmowidgets.core.location.BuildingDirectory

/**
 * The campus-map building of a timetable lesson. My ITMO's `main_bld_id` is the plan's building id (13, 273 for
 * every Ломоносова letter whose `bld_id` is 23, 33, 35 or 37); `bld_id` and then the directory's alias match cover
 * lessons without it. Null when the building has no plan in [planned].
 */
class PlanBuildingResolver(private val directory: BuildingDirectory) {
    fun resolve(mainBuildingId: Int?, buildingId: Int?, buildingName: String?, planned: Set<Int>): Int? =
        listOfNotNull(mainBuildingId, buildingId).firstOrNull { it in planned }
            ?: directory.find(buildingId, mainBuildingId, buildingName)?.planBuildingId?.takeIf { it in planned }
}
