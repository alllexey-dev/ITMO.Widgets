package dev.alllexey.itmowidgets.feature.map.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.map.domain.catalog.LoadedPlan
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapCatalog
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom

/** The campus plans from the mirror, kept on the device: everything works offline after the first download. */
interface CampusMapRepository {
    /** The mirror's buildings; the stored copy when the mirror cannot be reached. */
    suspend fun catalog(): AppResult<MapCatalog>

    /** A building's plan, downloaded once per version of its files. */
    suspend fun plan(buildingId: Int): AppResult<LoadedPlan>

    /** The rooms of every building, for the search across buildings. */
    suspend fun allRooms(): AppResult<Map<Int, List<MapRoom>>>
}
