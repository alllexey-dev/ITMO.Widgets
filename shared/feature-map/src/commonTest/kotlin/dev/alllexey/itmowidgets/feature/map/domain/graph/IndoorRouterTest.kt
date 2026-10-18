package dev.alllexey.itmowidgets.feature.map.domain.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndoorRouterTest {

    @Test
    fun theRouteLeavesTheStartRoomAndEndsAtTheFirstNodeOfTheTargetRoom() {
        val routes = IndoorRouter(graph).findRoutes(fromRoomId = 10, toRoomId = 30)

        assertEquals(listOf("3,4,5"), routes.map { it.key })
        assertEquals(setOf(RouteTrait.RECOMMENDED), routes.single().traits)
    }

    @Test
    fun stairsUpAndDownAreSeparatePartsOnTheirArrivalLevel() {
        val up = IndoorRouter(graph).findRoutes(10, 30).single().parts
        val down = IndoorRouter(graph).findRoutes(30, 10).single().parts

        assertEquals(listOf(RoutePartType.STAIRS_UP to 1, RoutePartType.FLOOR to 1), up.map { it.type to it.level })
        assertEquals(listOf(RoutePartType.STAIRS_DOWN to 0, RoutePartType.FLOOR to 0), down.map { it.type to it.level })
    }

    @Test
    fun aYardShortcutAddsTheIndoorAlternative() {
        val routes = IndoorRouter(graph).findRoutes(10, 40)

        assertEquals(2, routes.size)
        assertTrue(RouteTrait.RECOMMENDED in routes.first().traits)
        assertTrue(routes.first().parts.any { it.type == RoutePartType.OUTDOOR })
        assertEquals(setOf(RouteTrait.INDOOR), routes.last().traits)
    }

    @Test
    fun privateNodesAreNeverPassedAndUnknownRoomsHaveNoRoute() {
        assertTrue(IndoorRouter(graph).findRoutes(10, 50).isEmpty())
        assertTrue(IndoorRouter(graph).findRoutes(10, 999).isEmpty())
        assertTrue(IndoorRouter(graph).findRoutes(10, 10).isEmpty())
    }

    private companion object {
        fun node(id: Long, area: Long, level: Int, vararg links: Transition, outdoor: Boolean = false, private: Boolean = false) =
            GraphNode(id, area, level, 30.0 + id * 1e-4, 60.0, outdoor, private, links.toList())

        fun walk(to: Long, distance: Double = 3.0) = Transition(to, distance, TransitionType.STRAIGHT, 5.0)

        // Room 10 (nodes 1, 2) - corridor 20 (3) - stairs up to 4 on level 1 - room 30 (5, 6); a yard 7-8 from 2 to
        // room 40 (9), which is also reachable along corridor 11 - 12; room 50 (13) only through a private node 14.
        val graph = IndoorGraph(
            listOf(
                node(1, 10, 0, walk(2, 1.0)),
                node(2, 10, 0, walk(1, 1.0), walk(3), walk(7, 1.0), walk(11), walk(14)),
                node(3, 20, 0, walk(2), Transition(4, 5.0, TransitionType.STAIRS, 20.0)),
                node(4, 20, 1, Transition(3, 5.0, TransitionType.STAIRS, 1.0), walk(5)),
                node(5, 30, 1, walk(4), walk(6, 1.0)),
                node(6, 30, 1, walk(5, 1.0)),
                node(7, 60, 0, walk(2, 1.0), walk(8, 1.0), outdoor = true),
                node(8, 60, 0, walk(7, 1.0), walk(9, 1.0), outdoor = true),
                node(9, 40, 0, walk(8, 1.0), walk(12)),
                node(11, 20, 0, walk(2), walk(12, 10.0)),
                node(12, 20, 0, walk(11, 10.0), walk(9)),
                node(13, 50, 0, walk(14)),
                node(14, 70, 0, walk(2), walk(13), private = true),
            ),
        )
    }
}
