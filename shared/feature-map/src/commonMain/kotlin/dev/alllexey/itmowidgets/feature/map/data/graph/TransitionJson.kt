package dev.alllexey.itmowidgets.feature.map.data.graph

import dev.alllexey.itmowidgets.feature.map.domain.graph.GraphNode
import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorGraph
import dev.alllexey.itmowidgets.feature.map.domain.graph.Transition
import dev.alllexey.itmowidgets.feature.map.domain.graph.TransitionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** ITMO's `transition.json` as the mirror serves it unchanged: a GeoJSON collection of graph points. */
object TransitionJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(text: String): IndoorGraph =
        IndoorGraph(json.decodeFromString<Collection>(text).features.map { it.toNode() })

    @Serializable
    private class Collection(val features: List<Feature>)

    @Serializable
    private class Feature(val geometry: Geometry, val properties: Properties)

    /** `[longitude, latitude, level]`. */
    @Serializable
    private class Geometry(val coordinates: List<Double>)

    @Serializable
    private class Properties(
        val id: Long,
        @SerialName("area_id") val areaId: Long,
        val level: Int,
        val transitions: List<Link> = emptyList(),
        val outdoor: Boolean = false,
        val access: String? = null,
    )

    @Serializable
    private class Link(val id: Long, val distance: Double, val type: String, val weight: Double)

    private fun Feature.toNode() = GraphNode(
        id = properties.id,
        areaId = properties.areaId,
        level = properties.level,
        longitude = geometry.coordinates[0],
        latitude = geometry.coordinates[1],
        outdoor = properties.outdoor,
        private = properties.access == PRIVATE,
        transitions = properties.transitions.map { link ->
            Transition(
                to = link.id,
                distance = link.distance,
                type = if (link.type == STAIRS) TransitionType.STAIRS else TransitionType.STRAIGHT,
                weight = link.weight,
            )
        },
    )

    private const val PRIVATE = "private"
    private const val STAIRS = "stairs"
}
