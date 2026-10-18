package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.graph.RoutePart
import dev.alllexey.itmowidgets.feature.map.domain.graph.RoutePartType
import dev.alllexey.itmowidgets.feature.map.domain.trip.TripLeg
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings

/** A step with what the map shows for it: the [buildingId] and [level] of the plan, the [leg] and [part] drawn. */
internal data class PlannedStep(
    val view: TripStep,
    val buildingId: Int?,
    val level: Int?,
    val leg: TripLeg.Indoor?,
    val part: RoutePart?,
)

/**
 * Turns a trip into steps (docs/features/map.md "Screens"): one per part of each indoor route («По 1 этажу до
 * лестницы», «По лестнице на 3 этаж», «Через двор»), «По городу» with the target building for the system Maps, and
 * one plain step where a route is empty or missing. No lengths and no times.
 */
internal object TripSteps {
    fun of(legs: List<TripLeg>, label: (Int) -> String, destination: (Int) -> MapDestination?): List<PlannedStep> {
        val steps = legs.flatMapIndexed { index, leg ->
            when (leg) {
                is TripLeg.City -> listOf(city(leg, label, destination))
                is TripLeg.Indoor -> indoor(leg, last = index == legs.lastIndex)
            }
        }
        return steps.mapIndexed { index, step ->
            step.copy(view = step.view.copy(counter = UiText.Res(MapStrings.map_route_step, listOf(index + 1, steps.size))))
        }
    }

    private fun city(leg: TripLeg.City, label: (Int) -> String, destination: (Int) -> MapDestination?) = PlannedStep(
        view(TripStepKind.CITY, UiText.Res(MapStrings.map_step_city_title), UiText.Res(MapStrings.map_step_city, listOf(label(leg.toBuildingId)))),
        buildingId = null, level = null, leg = null, part = null,
    ).let { it.copy(view = it.view.copy(destination = destination(leg.toBuildingId))) }

    private fun indoor(leg: TripLeg.Indoor, last: Boolean): List<PlannedStep> {
        val parts = leg.route?.parts.orEmpty()
        if (parts.isEmpty()) {
            val target = RoomTexts.title(leg.to)
            val text = if (leg.from.id == leg.to.id) {
                UiText.Res(MapStrings.map_step_same_room, listOf(target))
            } else {
                UiText.Res(MapStrings.map_step_no_path, listOf(target, RoomTexts.floor(leg.to.level)))
            }
            return listOf(PlannedStep(view(TripStepKind.ARRIVED, target, text), leg.buildingId, leg.to.level, leg, null))
        }
        return parts.mapIndexed { index, part ->
            val next = parts.getOrNull(index + 1)
            val floor = part.level + 1
            val (kind, title, text) = when (part.type) {
                RoutePartType.FLOOR -> Triple(
                    TripStepKind.FLOOR,
                    UiText.Res(MapStrings.map_step_floor_title, listOf(floor)),
                    floorText(floor, next, leg, last),
                )
                RoutePartType.STAIRS_UP -> Triple(
                    TripStepKind.STAIRS_UP,
                    UiText.Res(MapStrings.map_step_up_title),
                    UiText.Res(MapStrings.map_step_up, listOf(floor)),
                )
                RoutePartType.STAIRS_DOWN -> Triple(
                    TripStepKind.STAIRS_DOWN,
                    UiText.Res(MapStrings.map_step_down_title),
                    UiText.Res(MapStrings.map_step_down, listOf(floor)),
                )
                RoutePartType.OUTDOOR -> Triple(
                    TripStepKind.YARD,
                    UiText.Res(MapStrings.map_step_yard_title),
                    UiText.Res(if (next?.type == RoutePartType.FLOOR) MapStrings.map_step_yard_to_entrance else MapStrings.map_step_yard),
                )
            }
            PlannedStep(view(kind, title, text), leg.buildingId, part.level, leg, part)
        }
    }

    private fun floorText(floor: Int, next: RoutePart?, leg: TripLeg.Indoor, last: Boolean): UiText = when {
        next?.type == RoutePartType.STAIRS_UP || next?.type == RoutePartType.STAIRS_DOWN ->
            UiText.Res(MapStrings.map_step_floor_to_stairs, listOf(floor))
        next?.type == RoutePartType.OUTDOOR -> UiText.Res(MapStrings.map_step_floor_to_exit, listOf(floor))
        next != null -> UiText.Res(MapStrings.map_step_floor, listOf(floor))
        last && leg.nearTarget -> UiText.Res(MapStrings.map_step_floor_near_target, listOf(floor, RoomTexts.title(leg.to)))
        last -> UiText.Res(MapStrings.map_step_floor_to_target, listOf(floor, RoomTexts.title(leg.to)))
        else -> UiText.Res(MapStrings.map_step_floor_to_entrance, listOf(floor))
    }

    private fun view(kind: TripStepKind, title: UiText, text: UiText) = TripStep(kind, title, text, UiText.Dynamic(""), null)
}
