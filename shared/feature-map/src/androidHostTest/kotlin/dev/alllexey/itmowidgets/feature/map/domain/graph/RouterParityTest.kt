package dev.alllexey.itmowidgets.feature.map.domain.graph

import dev.alllexey.itmowidgets.feature.map.data.graph.TransitionJson
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The Kotlin router against itmo-map-web's `router.js` (`scripts/map/router-fixtures.mjs`): the same routes node by
 * node, the same traits, parts and lengths. The synthetic graph always runs; ITMO's real graphs run when
 * `CAMPUS_MAP_GRAPHS` (a directory of `<id>.json`) and `CAMPUS_MAP_EXPECTED` (the script's `--graphs` output) are set,
 * since the plans are not committed here.
 */
class RouterParityTest {

    @Test
    fun `synthetic graph routes match router js`() {
        val cases = json.decodeFromString<List<Case>>(resource("synthetic-routes.json"))
        assertParity(TransitionJson.decode(resource("synthetic-graph.json")), cases)
    }

    @Test
    fun `real graphs match router js`() {
        val graphs = System.getenv("CAMPUS_MAP_GRAPHS")
        val expected = System.getenv("CAMPUS_MAP_EXPECTED")
        assumeTrue("CAMPUS_MAP_GRAPHS and CAMPUS_MAP_EXPECTED are not set", graphs != null && expected != null)
        val all = json.decodeFromString<Map<String, List<Case>>>(File(expected!!).readText())
        all.forEach { (building, cases) -> assertParity(TransitionJson.decode(File(graphs, "$building.json").readText()), cases) }
    }

    private fun assertParity(graph: IndoorGraph, cases: List<Case>) {
        val router = IndoorRouter(graph)
        cases.forEach { case ->
            val actual = router.findRoutes(case.from, case.to).map { route ->
                Expected(
                    key = route.key,
                    characteristics = route.traits.map { TRAIT_NAMES.getValue(it) },
                    parts = route.parts.map { Part(PART_NAMES.getValue(it.type), it.level, it.edges.size) },
                    distance = kotlin.math.floor(route.distance * 1000 + 0.5) / 1000,
                )
            }
            assertEquals("${case.from} -> ${case.to}", case.routes.map { it.sorted() }, actual.map { it.sorted() })
        }
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("router/$name")) { "router/$name" }.reader().readText()

    @Serializable
    private data class Case(val from: Long, val to: Long, val routes: List<Expected>)

    @Serializable
    private data class Expected(val key: String, val characteristics: List<String>, val parts: List<Part>, val distance: Double) {
        fun sorted() = copy(characteristics = characteristics.sorted())
    }

    @Serializable
    private data class Part(val type: String, val level: Int, val edges: Int)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val TRAIT_NAMES = mapOf(RouteTrait.INDOOR to "indoor", RouteTrait.RECOMMENDED to "recommended")
        val PART_NAMES = mapOf(
            RoutePartType.FLOOR to "floor",
            RoutePartType.STAIRS_UP to "stairsUp",
            RoutePartType.STAIRS_DOWN to "stairsDown",
            RoutePartType.OUTDOOR to "outdoor",
        )
    }
}
