package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings

/** Fictional map states for the previews, the screenshot baselines and the iOS snapshot tests. */
object CampusMapSamples {
    private val kronva = MapDestination("Кронверкский пр., 49", "Кронверкский проспект, 49, Санкт-Петербург", 59.9567751, 30.308711)

    private val scene = MapScene(
        buildingId = 13, tilesPath = "/sample/13.mbtiles", glyphsUrl = "",
        bounds = listOf(30.30767, 59.95599, 30.31108, 59.95753), bearing = 160.0, level = 3,
        route = CampusMapGeoJson.EMPTY, selection = CampusMapGeoJson.EMPTY, selectedRoomId = null, focus = null,
    )

    private val buildings = listOf(
        BuildingChip(13, "Кронверкский пр., 49", selected = true),
        BuildingChip(273, "ул. Ломоносова, 9", selected = false),
        BuildingChip(279, "пер. Гривцова, 14-16", selected = false),
    )

    private val levels = (0..4).map { LevelChip(it, UiText.Res(MapStrings.map_floor_short, listOf(it + 1)), it == 3) }

    private val places = listOf(Place.RESTROOMS, Place.FOOD, Place.COWORKING, Place.CLOAKROOM, Place.ENTRANCES)
        .map { PlaceChip(it, it.text, selected = false) }

    private val idle = SearchView("", places, emptyList(), emptyList())

    val Room = CampusMapUiState.Plan(
        map = scene,
        buildings = buildings,
        levels = levels,
        room = RoomCard(
            roomId = 71,
            title = UiText.Dynamic("2403"),
            subtitle = UiText.Joined(listOf(UiText.Res(MapStrings.map_kind_education), UiText.Res(MapStrings.map_floor, listOf(4))), " · "),
            note = null,
            icon = "auditory",
        ),
        trip = null,
        search = idle,
        notice = null,
    )

    val Approximate = Room.copy(
        room = Room.room!!.copy(
            title = UiText.Dynamic("1224"),
            note = UiText.Res(MapStrings.map_room_approximate, listOf("1224в", UiText.Dynamic("1224"))),
        ),
    )

    val Route = Room.copy(
        room = null,
        levels = levels.map { it.copy(onRoute = it.level == 0 || it.level == 3) },
        trip = TripView(
            from = UiText.Dynamic("1124"),
            to = UiText.Dynamic("2403"),
            steps = listOf(
                step(TripStepKind.FLOOR, UiText.Res(MapStrings.map_step_floor_title, listOf(1)), UiText.Res(MapStrings.map_step_floor_to_entrance, listOf(1)), 1),
                TripStep(
                    TripStepKind.CITY,
                    UiText.Res(MapStrings.map_step_city_title),
                    UiText.Res(MapStrings.map_step_city, listOf("Кронверкский пр., 49")),
                    UiText.Res(MapStrings.map_route_step, listOf(2, 4)),
                    kronva,
                ),
                step(TripStepKind.STAIRS_UP, UiText.Res(MapStrings.map_step_up_title), UiText.Res(MapStrings.map_step_up, listOf(4)), 3),
                step(TripStepKind.FLOOR, UiText.Res(MapStrings.map_step_floor_title, listOf(4)), UiText.Res(MapStrings.map_step_floor_to_target, listOf(4, UiText.Dynamic("2403"))), 4),
            ),
            current = 2,
        ),
    )

    val City = Route.copy(trip = Route.trip!!.copy(current = 1))

    /** «Отсюда» from 2403: where to is picked by the search, the entrances offered first. */
    val Pick = Route.copy(
        trip = TripView(from = UiText.Dynamic("2403"), to = null, steps = emptyList(), current = 0, picking = RouteEnd.TO),
        search = idle.copy(
            picking = RouteEnd.TO,
            results = listOf(SearchResult(13, 469, UiText.Res(MapStrings.map_kind_entrance), UiText.Res(MapStrings.map_floor, listOf(1)), "entrance")),
        ),
    )

    /** Another building on its way: the shown plan stays under the spinner. */
    val Switching = Room.copy(room = null, loading = true)

    val Search = Room.copy(
        room = null,
        search = SearchView(
            query = "14",
            places = places,
            results = listOf(
                SearchResult(13, 18, UiText.Dynamic("1404"), subtitle(UiText.Res(MapStrings.map_kind_education), UiText.Res(MapStrings.map_floor, listOf(4))), "auditory"),
                SearchResult(13, 262, UiText.Dynamic("1419"), subtitle(UiText.Dynamic("Lemon Classroom"), UiText.Res(MapStrings.map_floor, listOf(4))), "coworking"),
            ) + (1405..1410).map { number ->
                // More rows than the sheet holds: it stops at its height and scrolls.
                SearchResult(13, number.toLong(), UiText.Dynamic("$number"), subtitle(UiText.Res(MapStrings.map_kind_education), UiText.Res(MapStrings.map_floor, listOf(4))), "auditory")
            },
            elsewhere = listOf(
                SearchResult(273, 1, UiText.Dynamic("1410"), subtitle(UiText.Res(MapStrings.map_kind_education), UiText.Res(MapStrings.map_floor, listOf(1)), UiText.Dynamic("ул. Ломоносова, 9")), "auditory"),
            ),
        ),
    )

    val Missing = Room.copy(room = null, notice = UiText.Res(MapStrings.map_room_missing, listOf("9999")))

    val NoPlan = CampusMapUiState.NoPlan("Вяземский пер., 5-7", MapDestination("Вяземский пер., 5-7", "Вяземский переулок, 5-7", 59.97, 30.30))

    val Failed = CampusMapUiState.Failed(AppError.Network)

    private fun subtitle(vararg parts: UiText) = UiText.Joined(parts.toList(), " · ")

    private fun step(kind: TripStepKind, title: UiText, text: UiText, index: Int) =
        TripStep(kind, title, text, UiText.Res(MapStrings.map_route_step, listOf(index, 4)), null)
}
