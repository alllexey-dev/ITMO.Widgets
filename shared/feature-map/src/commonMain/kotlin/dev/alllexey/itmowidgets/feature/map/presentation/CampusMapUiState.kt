package dev.alllexey.itmowidgets.feature.map.presentation

import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapPoint

/** The campus map (`Карта`, docs/features/map.md): a building's plan with a room, a route or search over it. */
sealed interface CampusMapUiState {
    data object Loading : CampusMapUiState

    /** «Не удалось загрузить» with «Повторить». */
    data class Failed(val error: AppError) : CampusMapUiState

    /** «У корпуса нет плана»: the lesson's [building] and «Открыть в Картах» when its place is known. */
    data class NoPlan(val building: String, val destination: MapDestination?) : CampusMapUiState

    /**
     * The plan: what the native [map] view draws, the [buildings] and [levels] to switch, the selected [room] or the
     * [trip], the [search] panel and a [notice] about a lesson room that is not on the plan.
     */
    data class Plan(
        val map: MapScene,
        val buildings: List<BuildingChip>,
        val levels: List<LevelChip>,
        val room: RoomCard?,
        val trip: TripView?,
        val search: SearchView,
        val notice: UiText?,
    ) : CampusMapUiState
}

/**
 * What the platform map view draws: the [tilesPath] of the building's MBTiles on [level] with [selectedRoomId]
 * highlighted, the [route] and [selection] GeoJSON (`CampusMapLayers`), and where the camera goes: the route step's [frame] (west, south, east, north), else
 * the [focus] point, else the building's [bounds].
 */
data class MapScene(
    val buildingId: Int,
    val tilesPath: String,
    val glyphsUrl: String,
    val bounds: List<Double>,
    val bearing: Double,
    val level: Int,
    val route: String,
    val selection: String,
    val selectedRoomId: Long?,
    val focus: MapPoint?,
    val frame: List<Double>? = null,
)

/** A building of the switcher: the directory's short [label]. */
data class BuildingChip(val id: Int, val label: String, val selected: Boolean)

/** A floor of the switcher: «1» for level 0; [onRoute] marks a floor the shown route passes, as itmo-map-web does. */
data class LevelChip(val level: Int, val label: UiText, val selected: Boolean, val onRoute: Boolean = false)

/** The selected room: «2403», «4 этаж, Кронверкский пр., 49», a [note] when the lesson room only nearly matched. */
data class RoomCard(
    val roomId: Long,
    val title: UiText,
    val subtitle: UiText,
    val note: UiText?,
    val destination: MapDestination?,
)

/** «Маршрут»: where it starts ([from]), its [steps] and the one shown. */
data class TripView(val from: UiText, val steps: List<TripStep>, val current: Int) {
    val step: TripStep get() = steps[current]
}

/** One step: a floor, a stair, the yard, or «По городу» with the system Maps [destination]. */
data class TripStep(val kind: TripStepKind, val title: UiText, val text: UiText, val counter: UiText, val destination: MapDestination?)

enum class TripStepKind { FLOOR, STAIRS_UP, STAIRS_DOWN, YARD, CITY, ARRIVED }

/** Search over the shown building, then [elsewhere] in the others; [places] filter by kind when the query is empty. */
data class SearchView(
    val query: String,
    val places: List<PlaceChip>,
    val results: List<SearchResult>,
    val elsewhere: List<SearchResult>,
) {
    val active: Boolean get() = query.isNotBlank() || places.any { it.selected }
    val empty: Boolean get() = active && results.isEmpty() && elsewhere.isEmpty()
}

data class PlaceChip(val place: Place, val label: UiText, val selected: Boolean)

/** A room found: «1124», «1 этаж, Ломоносова» (the building only in [CampusMapUiState.Plan.search]'s elsewhere). */
data class SearchResult(val buildingId: Int, val roomId: Long, val title: UiText, val subtitle: UiText)
