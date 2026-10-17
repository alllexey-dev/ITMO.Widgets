package dev.alllexey.itmowidgets.feature.map.domain.trip

import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorGraph
import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorRouter
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom

/** A building that has a plan: its [rooms] and its routing [graph]. */
class BuildingPlan(val buildingId: Int, val rooms: List<MapRoom>, val graph: IndoorGraph) {
    val router: IndoorRouter by lazy { IndoorRouter(graph) }

    /** The entrance that has a graph node; every mirrored building has exactly one. */
    val entrance: MapRoom? get() = rooms.firstOrNull { it.isEntrance && graph.nodeForRoom(it.id) != null }

    fun room(id: Long): MapRoom? = rooms.firstOrNull { it.id == id }
}
