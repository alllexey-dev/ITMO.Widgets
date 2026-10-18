package dev.alllexey.itmowidgets.feature.map.domain.trip

import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorGraph
import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorRouter
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import kotlin.math.PI
import kotlin.math.cos

/** A building that has a plan: its [rooms] and its routing [graph]. */
class BuildingPlan(val buildingId: Int, val rooms: List<MapRoom>, val graph: IndoorGraph) {
    val router: IndoorRouter by lazy { IndoorRouter(graph) }

    /** The entrance that has a graph node; every mirrored building has exactly one. */
    val entrance: MapRoom? get() = rooms.firstOrNull { it.isEntrance && graph.nodeForRoom(it.id) != null }

    fun room(id: Long): MapRoom? = rooms.firstOrNull { it.id == id }

    /** The areas whose graph points on [room]'s floor lie closest to its label: where to go when it has no way in. */
    fun areasNear(room: MapRoom, limit: Int): List<Long> {
        val scale = cos(room.latitude * PI / 180)
        return graph.nodes.asSequence()
            .filter { it.level == room.level && !it.private && it.areaId != room.id }
            .sortedBy { node ->
                val dx = (node.longitude - room.longitude) * scale
                val dy = node.latitude - room.latitude
                dx * dx + dy * dy
            }
            .map { it.areaId }
            .distinct()
            .take(limit)
            .toList()
    }
}
