package dev.alllexey.itmowidgets.feature.map.domain.graph

import kotlin.math.floor

/**
 * The router of the ITMO campus app, ported from itmo-map-web `web/public/router.js` (docs/features/map.md): Dijkstra
 * over [graph] with costs `round(weight * distance)`, free edges at both ends, no private points. Two candidates, the
 * plain route and one without yard points; duplicates dropped, cheapest first and recommended.
 */
class IndoorRouter(private val graph: IndoorGraph) {
    private val open = graph.nodes.filterNot { it.private }
    private val openIds = open.mapTo(HashSet()) { it.id }

    /** Routes from room [fromRoomId] to room [toRoomId], recommended first; empty when either has no node or no way. */
    fun findRoutes(fromRoomId: Long, toRoomId: Long): List<IndoorRoute> {
        val from = graph.nodeForRoom(fromRoomId) ?: return emptyList()
        val to = graph.nodeForRoom(toRoomId) ?: return emptyList()
        val candidates = listOf(
            emptySet<RouteTrait>() to path(from.id, to.id) { true },
            setOf(RouteTrait.INDOOR) to path(from.id, to.id) { !it.outdoor },
        )
        // One candidate through a private point drops both, as in the original.
        if (candidates.any { it.second == null }) return emptyList()
        val routes = mutableListOf<IndoorRoute>()
        for ((traits, nodes) in candidates) {
            if (nodes.isNullOrEmpty()) continue
            val route = IndoorRoute(nodes.zipWithNext(::RouteEdge), traits).shrunk(fromRoomId, toRoomId)
            if (routes.none { it.key == route.key }) routes += route
        }
        return routes.sortedBy { it.weight }.mapIndexed { index, route ->
            if (index == 0) route.copy(traits = route.traits + RouteTrait.RECOMMENDED) else route
        }
    }

    /** Node ids from [start] to [end] over the points that pass [keep]; empty when unreachable, null when private. */
    private fun path(start: Long, end: Long, keep: (GraphNode) -> Boolean): List<GraphNode>? {
        val edges = HashMap<Long, LinkedHashMap<Long, Long>>()
        for (node in open) {
            if (!keep(node)) continue
            val out = LinkedHashMap<Long, Long>()
            for (t in node.transitions) {
                val touchesEnds = node.id == start || node.id == end || t.to == start || t.to == end
                out[t.to] = if (touchesEnds) 0L else jsRound(t.weight * t.distance)
            }
            edges[node.id] = out
        }
        val ids = dijkstra(edges, start, end)
        if (ids.any { it !in openIds }) return null
        return ids.map { graph[it] ?: return null }
    }

    private companion object {
        /** `dijkstrajs`: a sorted open list with stable insertion and no visited set; empty if unreachable or [s] == [d]. */
        fun dijkstra(graph: Map<Long, Map<Long, Long>>, s: Long, d: Long): List<Long> {
            val predecessors = HashMap<Long, Long>()
            val costs = hashMapOf(s to 0L)
            val queue = ArrayList<Pair<Long, Long>>().apply { add(s to 0L) }
            while (queue.isNotEmpty()) {
                val (u, costU) = queue.removeAt(0)
                for ((v, costE) in graph[u].orEmpty()) {
                    val total = costU + costE
                    val costV = costs[v]
                    if (costV == null || costV > total) {
                        costs[v] = total
                        predecessors[v] = u
                        var i = queue.size
                        while (i > 0 && queue[i - 1].second > total) i--
                        queue.add(i, v to total)
                    }
                }
            }
            if (d !in costs) return emptyList()
            val nodes = generateSequence(d) { predecessors[it] }.toList()
            return if (nodes.size == 1) emptyList() else nodes.reversed()
        }

        /** JavaScript's `Math.round` for the non-negative costs here. */
        fun jsRound(value: Double): Long = floor(value + 0.5).toLong()
    }
}
