package dev.alllexey.itmowidgets.feature.map.domain.trip

import dev.alllexey.itmowidgets.feature.map.domain.graph.IndoorRoute
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom

/** A room on a plan: where a trip starts or ends. */
data class PlanPlace(val buildingId: Int, val room: MapRoom)

/** Where the previous lesson was: a room on a plan, or only a building (no plan, or the room is not on it). */
sealed interface TripStart {
    val buildingId: Int

    data class Room(val place: PlanPlace) : TripStart {
        override val buildingId: Int get() = place.buildingId
    }

    data class Building(override val buildingId: Int) : TripStart
}

/** One part of a «Маршрут»: walking inside a building, or «По городу» between two buildings in the system Maps. */
sealed interface TripLeg {
    /**
     * From [from] to [to] in [buildingId]; [route] is null when the graph has no way (the rooms are still shown).
     * [nearTarget]: ITMO's graph has no way into [to] (about 7% of the rooms), so [route] ends at the closest place.
     */
    data class Indoor(
        val buildingId: Int,
        val from: MapRoom,
        val to: MapRoom,
        val route: IndoorRoute?,
        val nearTarget: Boolean = false,
    ) : TripLeg

    data class City(val fromBuildingId: Int, val toBuildingId: Int) : TripLeg
}

/**
 * Plans «Маршрут» (docs/features/map.md): from the previous lesson's room, or from the target building's entrance
 * when there is none; another building is three legs, to its exit, «По городу», from the entrance to the room. A
 * start without a room on a plan leaves «По городу» and the last leg. Empty when the target has no plan.
 */
object TripPlanner {
    fun plan(from: TripStart?, to: PlanPlace, plans: (Int) -> BuildingPlan?): List<TripLeg> {
        val target = plans(to.buildingId) ?: return emptyList()
        if (from is TripStart.Room && from.buildingId == to.buildingId) {
            return listOf(indoor(target, from.place.room, to.room))
        }
        val arrival = target.entrance?.let { indoor(target, it, to.room) } ?: return emptyList()
        if (from == null || from.buildingId == to.buildingId) return listOf(arrival)
        val departure = (from as? TripStart.Room)?.let { start ->
            plans(start.buildingId)?.let { plan -> plan.entrance?.let { indoor(plan, start.place.room, it) } }
        }
        return listOfNotNull(departure, TripLeg.City(from.buildingId, to.buildingId), arrival)
    }

    private fun indoor(plan: BuildingPlan, from: MapRoom, to: MapRoom): TripLeg.Indoor {
        plan.router.findRoutes(from.id, to.id).firstOrNull()?.let { return TripLeg.Indoor(plan.buildingId, from, to, it) }
        if (from.id == to.id) return TripLeg.Indoor(plan.buildingId, from, to, null)
        val near = plan.areasNear(to, NEAR_AREAS).firstNotNullOfOrNull { area -> plan.router.findRoutes(from.id, area).firstOrNull() }
        return TripLeg.Indoor(plan.buildingId, from, to, near, nearTarget = near != null)
    }

    private const val NEAR_AREAS = 8
}
