package dev.alllexey.itmowidgets.feature.map.domain.trip

import dev.alllexey.itmowidgets.feature.map.domain.graph.GraphNode
import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorGraph
import dev.alllexey.itmowidgets.feature.map.domain.graph.Transition
import dev.alllexey.itmowidgets.feature.map.domain.graph.TransitionType
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TripPlannerTest {

    @Test
    fun withoutAPreviousLessonTheRouteStartsAtTheEntrance() {
        val legs = TripPlanner.plan(from = null, to = PlanPlace(13, kronva.room(3)!!), plans::get)

        val leg = assertIs<TripLeg.Indoor>(legs.single())
        assertEquals(1L, leg.from.id)
        assertNotNull(leg.route)
    }

    @Test
    fun theSameBuildingGoesFromRoomToRoom() {
        val legs = TripPlanner.plan(TripStart.Room(PlanPlace(13, kronva.room(2)!!)), PlanPlace(13, kronva.room(3)!!), plans::get)

        assertEquals(2L, assertIs<TripLeg.Indoor>(legs.single()).from.id)
    }

    @Test
    fun anotherBuildingIsToTheExitThenTheCityThenFromTheEntrance() {
        val legs = TripPlanner.plan(TripStart.Room(PlanPlace(273, lomo.room(3)!!)), PlanPlace(13, kronva.room(3)!!), plans::get)

        assertEquals(3, legs.size)
        val exit = assertIs<TripLeg.Indoor>(legs[0])
        assertEquals(273 to 1L, exit.buildingId to exit.to.id)
        assertEquals(TripLeg.City(273, 13), legs[1])
        assertEquals(13 to 1L, assertIs<TripLeg.Indoor>(legs[2]).let { it.buildingId to it.from.id })
    }

    @Test
    fun aStartWithoutAPlanSkipsTheExitLeg() {
        val legs = TripPlanner.plan(TripStart.Building(5), PlanPlace(13, kronva.room(3)!!), plans::get)

        assertEquals(TripLeg.City(5, 13), legs.first())
        assertEquals(2, legs.size)
        assertEquals(1, TripPlanner.plan(TripStart.Building(13), PlanPlace(13, kronva.room(3)!!), plans::get).size)
    }

    @Test
    fun aRoomWithoutAWayInIsReachedAsCloseAsThePlanGoes() {
        // Room 9 has graph points of its own but no transition into them, as 1206 in ITMO's 273.
        val walled = BuildingPlan(
            13,
            kronva.rooms + MapRoom(9, 13, 0, "room", "education", "9", "9", null, 30.0, 60.0),
            IndoorGraph(kronva.graph.nodes + GraphNode(9, 9, 0, 30.0, 60.0, false, false, emptyList())),
        )
        val leg = assertIs<TripLeg.Indoor>(TripPlanner.plan(null, PlanPlace(13, walled.room(9)!!)) { walled }.single())

        assertTrue(leg.nearTarget)
        assertNotNull(leg.route)
        assertEquals(9L, leg.to.id)
    }

        @Test
    fun aTargetWithoutAPlanHasNoTrip() {
        assertTrue(TripPlanner.plan(null, PlanPlace(5, kronva.room(3)!!), plans::get).isEmpty())
    }

    private companion object {
        fun plan(id: Int): BuildingPlan {
            fun room(roomId: Long, kind: String) = MapRoom(roomId, id, 0, "room", kind, "$roomId", "$roomId", null, 30.0, 60.0)
            fun walk(to: Long) = Transition(to, 3.0, TransitionType.STRAIGHT, 5.0)
            val nodes = listOf(
                GraphNode(1, 1, 0, 30.0, 60.0, false, false, listOf(walk(2))),
                GraphNode(2, 2, 0, 30.0, 60.0, false, false, listOf(walk(1), walk(3))),
                GraphNode(3, 3, 0, 30.0, 60.0, false, false, listOf(walk(2))),
            )
            return BuildingPlan(id, listOf(room(1, "entrance"), room(2, "education"), room(3, "education")), IndoorGraph(nodes))
        }

        val kronva = plan(13)
        val lomo = plan(273)
        val plans = mapOf(13 to kronva, 273 to lomo)
    }
}
