package dev.alllexey.itmowidgets.feature.map.domain.graph

/** What a route is: [INDOOR] avoids the yard, [RECOMMENDED] is the cheapest of the candidates. */
enum class RouteTrait { INDOOR, RECOMMENDED }

/** One step between neighbouring nodes. */
data class RouteEdge(val from: GraphNode, val to: GraphNode)

/** A run of edges of one kind on one [level]: walking a floor, a stair up or down, the yard. */
data class RoutePart(val type: RoutePartType, val level: Int, val edges: List<RouteEdge>)

enum class RoutePartType { FLOOR, STAIRS_UP, STAIRS_DOWN, OUTDOOR }

/** A route through one building's graph as a list of [edges]; [weight] orders candidates, nothing shows it. */
data class IndoorRoute(val edges: List<RouteEdge>, val traits: Set<RouteTrait>) {

    /** Node ids in order; equal keys are the same route. */
    val key: String
        get() = (edges.map { it.from.id } + listOfNotNull(edges.lastOrNull()?.to?.id)).joinToString(",")

    val weight: Double get() = edges.sumOf { edge -> transition(edge)?.let { it.weight * it.distance } ?: 0.0 }

    /** Metres; computed for parity with the original, the app shows no lengths or times. */
    val distance: Double get() = edges.sumOf { transition(it)?.distance ?: 0.0 }

    val start: GraphNode? get() = edges.firstOrNull()?.from
    val end: GraphNode? get() = edges.lastOrNull()?.to

    /** Consecutive edges of one [RoutePartType] joined; a part's level is where its last edge arrives. */
    val parts: List<RoutePart>
        get() {
            val runs = mutableListOf<Pair<RoutePartType, MutableList<RouteEdge>>>()
            for (edge in edges) {
                val type = partType(edge)
                val last = runs.lastOrNull()
                if (last != null && last.first == type) last.second += edge else runs += type to mutableListOf(edge)
            }
            return runs.map { (type, run) -> RoutePart(type, run.last().to.level, run) }
        }

    /**
     * Drops the walking inside the start room and inside the target room, keeping at least one edge: the intended
     * behaviour of the campus app's `shrinkPath`, whose index bug removes every other edge instead.
     */
    internal fun shrunk(fromRoomId: Long, toRoomId: Long): IndoorRoute {
        val path = edges.toMutableList()
        while (path.isNotEmpty() && path.first().from.areaId == fromRoomId) path.removeAt(0)
        while (path.size > 1 && path.last().from.areaId == toRoomId) path.removeAt(path.lastIndex)
        return copy(edges = path)
    }

    private fun transition(edge: RouteEdge): Transition? = edge.from.transitions.firstOrNull { it.to == edge.to.id }

    private fun partType(edge: RouteEdge): RoutePartType {
        val t = transition(edge)
        return when {
            t?.type == TransitionType.STAIRS -> if (t.weight > 1) RoutePartType.STAIRS_UP else RoutePartType.STAIRS_DOWN
            edge.to.outdoor -> RoutePartType.OUTDOOR
            else -> RoutePartType.FLOOR
        }
    }
}
