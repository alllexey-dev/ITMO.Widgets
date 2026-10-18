package dev.alllexey.itmowidgets.feature.map.domain.graph

/**
 * A building's routing graph (ITMO's `transition.json`): points on floors linked to their neighbours. [nodes] keep
 * the file order, which decides ties the same way as the campus app and itmo-map-web.
 */
class IndoorGraph(val nodes: List<GraphNode>) {
    private val byId: Map<Long, GraphNode> = nodes.associateBy { it.id }

    operator fun get(id: Long): GraphNode? = byId[id]

    /** The first node of a room (plan room id), as the campus app picks it. */
    fun nodeForRoom(roomId: Long): GraphNode? = nodes.firstOrNull { it.areaId == roomId }
}

/**
 * A point of the graph: [areaId] is the plan room it lies in, [level] the floor (`0` is the first), [outdoor] a yard
 * point, [private] a point routes never pass.
 */
data class GraphNode(
    val id: Long,
    val areaId: Long,
    val level: Int,
    val longitude: Double,
    val latitude: Double,
    val outdoor: Boolean,
    val private: Boolean,
    val transitions: List<Transition>,
)

/** A link to the node [to]: [distance] in metres, [weight] the cost per metre (stairs up cost more than down). */
data class Transition(val to: Long, val distance: Double, val type: TransitionType, val weight: Double)

enum class TransitionType { STRAIGHT, STAIRS }
