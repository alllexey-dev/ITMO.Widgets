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
     * [trip], the [search] panel and a [notice] about a lesson room that is not on the plan. [loading] while a building
     * or a route is on its way: the plan stays and a spinner says so.
     */
    data class Plan(
        val map: MapScene,
        val buildings: List<BuildingChip>,
        val levels: List<LevelChip>,
        val room: RoomCard?,
        val trip: TripView?,
        val search: SearchView,
        val notice: UiText?,
        val loading: Boolean = false,
    ) : CampusMapUiState
}

/**
 * What the platform map view draws: the [tilesPath] of the building's MBTiles on [level] with [selectedRoomId]
 * highlighted, the [route] and [selection] GeoJSON (`CampusMapLayers`), and where the camera goes: the route step's [frame] (west, south, east, north), else
 * the [focus] point, else the building's [bounds]; each new [fit] (`Показать корпус целиком`) frames the bounds again.
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
    val fit: Int = 0,
)

/** A building of the switcher: the directory's short [label]. */
data class BuildingChip(val id: Int, val label: String, val selected: Boolean)

/** A floor of the switcher: «1» for level 0; [onRoute] marks a floor the shown route passes, as itmo-map-web does. */
data class LevelChip(val level: Int, val label: UiText, val selected: Boolean, val onRoute: Boolean = false)

/**
 * The selected room: «2403», «4 этаж, Кронверкский пр., 49», a [note] when the lesson room only nearly matched; [icon]
 * is the map sprite of its kind (`CampusMapStyle.SPRITES`).
 */
data class RoomCard(
    val roomId: Long,
    val title: UiText,
    val subtitle: UiText,
    val note: UiText?,
    val icon: String,
)

/** An end of the route: А, where it starts, or Б, where it goes. */
enum class RouteEnd { FROM, TO }

/**
 * «Маршрут» from [from] (А) to [to] (Б, null until chosen), its [steps] and the one shown. While [picking] an end, the
 * search and a tap on the plan choose it. [steps] is empty without [to]; [notFound] when the plan has no way at all.
 */
data class TripView(
    val from: UiText,
    val to: UiText?,
    val steps: List<TripStep>,
    val current: Int,
    val picking: RouteEnd? = null,
    val notFound: Boolean = false,
) {
    val step: TripStep? get() = steps.getOrNull(current)
}

/** One step: a floor, a stair, the yard, or «По городу» with the system Maps [destination]. */
data class TripStep(val kind: TripStepKind, val title: UiText, val text: UiText, val counter: UiText, val destination: MapDestination?)

/** What a step is, with its map sprite (itmo-map-web's step icons). */
enum class TripStepKind(val icon: String) {
    FLOOR("walkway"),
    STAIRS_UP("stairs.up"),
    STAIRS_DOWN("stairs.down"),
    YARD("yard"),
    CITY("entrance"),
    ARRIVED("default"),
}

/**
 * Search over the shown building, then [elsewhere] in the others; [places] filter by kind when the query is empty.
 * While [picking] a route end it is open from the start, with the building's entrances before anything is typed.
 */
data class SearchView(
    val query: String,
    val places: List<PlaceChip>,
    val results: List<SearchResult>,
    val elsewhere: List<SearchResult>,
    val picking: RouteEnd? = null,
) {
    val active: Boolean get() = query.isNotBlank() || places.any { it.selected } || picking != null
    val empty: Boolean get() = active && results.isEmpty() && elsewhere.isEmpty()
}

data class PlaceChip(val place: Place, val label: UiText, val selected: Boolean) {
    val icon: String get() = place.icon
}

/**
 * A room found: «1124», «1 этаж, Ломоносова» (the building only in [CampusMapUiState.Plan.search]'s elsewhere), with
 * the map sprite of its kind.
 */
data class SearchResult(val buildingId: Int, val roomId: Long, val title: UiText, val subtitle: UiText, val icon: String = "default")
