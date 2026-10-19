package dev.alllexey.itmowidgets.feature.map.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.MapDestination
import dev.alllexey.itmowidgets.core.navigation.CampusMapArgs
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.domain.CampusMapRepository
import dev.alllexey.itmowidgets.feature.map.domain.catalog.LoadedPlan
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapCatalog
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapPoint
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.feature.map.domain.rooms.PlanBuildingResolver
import dev.alllexey.itmowidgets.feature.map.domain.rooms.RoomMatch
import dev.alllexey.itmowidgets.feature.map.domain.rooms.RoomMatcher
import dev.alllexey.itmowidgets.feature.map.domain.trip.PlanPlace
import dev.alllexey.itmowidgets.feature.map.domain.trip.TripPlanner
import dev.alllexey.itmowidgets.feature.map.domain.trip.TripStart
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * The campus map (docs/features/map.md): opened with a lesson (`ARG`, [CampusMapArgs]) it shows that lesson's room,
 * and with `route` the «Маршрут» to it from the day's previous in-person lesson or the entrance; without one it
 * opens the first building to browse. Search runs over the shown building, then over the others. A route goes
 * between any two rooms, in one building or two: «Сюда» and «Отсюда» on a room, then either end is picked again by
 * the search or a tap on the plan. Routing runs on [AppDispatchers.default]; a building or a route on its way sets
 * [CampusMapUiState.Plan.loading].
 */
class CampusMapViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CampusMapRepository,
    private val directory: BuildingDirectory,
    private val days: DayLessonsGateway,
    private val dispatchers: AppDispatchers,
) : ViewModel() {

    private val args: CampusMapArgs = savedStateHandle.get<String>(ARG)?.let(::decode) ?: CampusMapArgs()
    private val resolver = PlanBuildingResolver(directory)
    private val model = MutableStateFlow(Model())
    private var work: Job? = null

    val uiState: StateFlow<CampusMapUiState> = model.map(::present)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CampusMapUiState.Loading)

    init {
        start()
    }

    fun retry() = start()

    fun selectBuilding(buildingId: Int) {
        if (model.value.plan?.building?.id == buildingId) return
        launchLoad { show(buildingId, room = null) }
    }

    fun selectLevel(level: Int) = model.update { it.copy(level = level, focus = null) }

    /** A tap on the plan: the room under it, or nothing to clear the card; while picking a route end, that end. */
    fun selectRoom(roomId: Long?) {
        val state = model.value
        val room = roomId?.let { id -> state.plan?.plan?.room(id) }
        val end = state.picking
        if (end != null && room != null) return launchLoad { assign(end, PlanPlace(room.buildingId, room)) }
        model.update { current ->
            current.copy(selected = room?.id, approximate = current.approximate.takeIf { room?.id == current.target?.room?.id }, focus = null)
        }
    }

    fun search(query: String) {
        // A text field may hand back the text it already shows (iOS commits it when the field loses focus): that
        // is no edit and must not close the route it just opened.
        if (query == model.value.query) return
        model.update { it.copy(query = query, place = null, trip = it.trip.takeIf { _ -> it.picking != null }) }
        if (query.isNotBlank() && model.value.allRooms == null) {
            viewModelScope.launch {
                val rooms = repository.allRooms()
                if (rooms is AppResult.Success) model.update { it.copy(allRooms = rooms.value) }
            }
        }
    }

    fun selectPlace(place: Place?) = model.update { state ->
        state.copy(place = place.takeIf { it != state.place }, query = "", trip = state.trip.takeIf { state.picking != null })
    }

    fun openResult(result: SearchResult) = launchLoad {
        model.value.picking?.let { end ->
            val room = loadPlan(result.buildingId)?.plan?.room(result.roomId) ?: return@launchLoad
            return@launchLoad assign(end, PlanPlace(result.buildingId, room))
        }
        if (model.value.plan?.building?.id != result.buildingId) show(result.buildingId, room = null)
        val room = model.value.plan?.plan?.room(result.roomId) ?: return@launchLoad
        model.update { it.copy(selected = room.id, level = room.level, focus = room.point, query = "", place = null) }
    }

    /** «Сюда» to the selected room: from the previous lesson for the lesson's own room, else from the entrance. */
    fun route() = launchLoad { routeNow() }

    /** «Отсюда»: the route from the selected room; where to is picked next. */
    fun routeFrom() {
        val state = model.value
        val room = state.selected?.let { id -> state.plan?.plan?.room(id) } ?: return
        model.update { it.copy(trip = Trip(TripStart.Room(PlanPlace(room.buildingId, room)), to = null), picking = RouteEnd.TO, query = "", place = null) }
    }

    /** Picks [end] of the route anew by the search or a tap on the plan; the same end again, or null, stops picking. */
    fun pick(end: RouteEnd?) = model.update { state ->
        state.copy(picking = end.takeIf { state.trip != null && it != state.picking }, query = "", place = null)
    }

    /** Swaps А and Б; the entrance a route starts at becomes its end. */
    fun swap() = launchLoad {
        val trip = model.value.trip ?: return@launchLoad
        val to = when (val from = trip.from) {
            is TripStart.Room -> from.place
            is TripStart.Building -> entrance(from.buildingId)
            null -> trip.to?.let { entrance(it.buildingId) }
        }
        planTrip(trip.to?.let(TripStart::Room), to)
    }

    fun step(index: Int) = launchLoad { stepNow(index) }

    private suspend fun routeNow() {
        val state = model.value
        val plan = state.plan ?: return
        val room = plan.plan.room(state.selected ?: return) ?: return
        val start = if (room.id == state.target?.room?.id) previousStart(plan.building.id) else null
        planTrip(start, PlanPlace(plan.building.id, room))
    }

    private suspend fun assign(end: RouteEnd, place: PlanPlace) {
        val trip = model.value.trip ?: return
        when (end) {
            RouteEnd.FROM -> planTrip(TripStart.Room(place), trip.to)
            RouteEnd.TO -> planTrip(trip.from, place)
        }
    }

    /** The route from [from] (null: the target building's entrance) to [to]; without [to], picking it comes next. */
    private suspend fun planTrip(from: TripStart?, to: PlanPlace?) {
        if (to == null) {
            model.update { it.copy(trip = Trip(from, null), picking = RouteEnd.TO, query = "", place = null) }
            return
        }
        model.update { it.copy(loading = true) }
        val plans = HashMap<Int, LoadedPlan>()
        loadPlan(to.buildingId)?.let { plans[to.buildingId] = it }
        (from as? TripStart.Room)?.let { start -> loadPlan(start.buildingId)?.let { plans[start.buildingId] = it } }
        // Dijkstra over a building's thousands of points, up to nine times for a room without a way in.
        val legs = withContext(dispatchers.default) { TripPlanner.plan(from, to) { id -> plans[id]?.plan } }
        val steps = TripSteps.of(legs, ::label, ::destination)
        model.update { it.copy(trip = Trip(from, to, steps, plans), picking = null, query = "", place = null) }
        stepNow(0)
    }

    private suspend fun entrance(buildingId: Int): PlanPlace? =
        loadPlan(buildingId)?.plan?.entrance?.let { PlanPlace(buildingId, it) }

    private suspend fun stepNow(index: Int) {
        val trip = model.value.trip ?: return
        val step = trip.steps.getOrNull(index) ?: return
        val building = step.buildingId
        if (building != null && model.value.plan?.building?.id != building) {
            val plan = trip.plans[building] ?: loadPlan(building) ?: return
            model.update { it.copy(plan = plan) }
        }
        val focus = step.part?.edges?.firstOrNull()?.from?.let { MapPoint(it.longitude, it.latitude) }
            ?: step.leg?.to?.point
        model.update { state ->
            state.copy(
                trip = state.trip?.copy(current = index),
                level = step.level ?: state.level,
                selected = step.leg?.to?.id ?: state.selected,
                focus = focus,
            )
        }
    }

    fun closeTrip() = model.update { it.copy(trip = null, picking = null) }

    fun closeRoom() = model.update { it.copy(selected = null, approximate = null) }

    private fun start() = launchLoad {
        model.value = Model()
        val catalog = when (val result = repository.catalog()) {
            is AppResult.Failure -> return@launchLoad model.update { it.copy(failure = result.error) }
            is AppResult.Success -> result.value
        }
        model.update { it.copy(catalog = catalog) }
        val lesson = args.lesson
        if (lesson == null) {
            catalog.buildings.firstOrNull()?.let { show(it.id, room = null) } ?: model.update { it.copy(failure = AppError.NotFound) }
            return@launchLoad
        }
        val building = resolver.resolve(lesson.mainBuildingId, lesson.buildingId, lesson.building, catalog.ids)
        if (building == null) {
            val known = directory.find(lesson.buildingId, lesson.mainBuildingId, lesson.building)
            val name = known?.label ?: lesson.building?.trim().orEmpty()
            val destination = known?.toMapDestination() ?: lesson.building?.trim()?.takeIf(String::isNotEmpty)?.let { MapDestination(it, it) }
            model.update { it.copy(noPlan = CampusMapUiState.NoPlan(name, destination)) }
            return@launchLoad
        }
        show(building, lesson.room)
        if (args.route && model.value.selected != null) routeNow()
    }

    /** Shows [buildingId]'s plan, on the floor of the lesson [room] when it is on it, else on the entrance floor. */
    private suspend fun show(buildingId: Int, room: String?) {
        val plan = loadPlan(buildingId) ?: return
        val match = room?.trim()?.takeIf(String::isNotEmpty)?.let { RoomMatcher.match(it, plan.rooms) }
        val level = match?.room?.level ?: plan.plan.entrance?.level ?: plan.building.levels.firstOrNull { it >= 0 } ?: 0
        model.update { state ->
            state.copy(
                plan = plan,
                level = level,
                selected = match?.room?.id,
                target = if (room != null) Target(room, match?.room) else state.target,
                approximate = match?.takeIf(RoomMatch::approximate),
                focus = match?.room?.point,
                // Picking a route end may look for it in another building; anything else leaves the route.
                trip = state.trip.takeIf { state.picking != null },
            )
        }
    }

    private suspend fun loadPlan(buildingId: Int): LoadedPlan? {
        model.value.plan?.takeIf { it.building.id == buildingId }?.let { return it }
        model.value.trip?.plans?.get(buildingId)?.let { return it }
        model.update { it.copy(loading = true) }
        return fetchPlan(buildingId)
    }

    private suspend fun fetchPlan(buildingId: Int): LoadedPlan? = when (val result = repository.plan(buildingId)) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> {
            // Over a shown plan a failed switch keeps the plan; without one the screen shows the error.
            model.update { if (it.plan == null) it.copy(failure = result.error) else it }
            null
        }
    }

    /** Where the previous in-person lesson of the target's day was, on a plan when the mirror has one. */
    private suspend fun previousStart(targetBuilding: Int): TripStart? {
        val target = args.lesson ?: return null
        val previous = previousLesson(target) ?: return null
        val catalog = model.value.catalog ?: return null
        val building = resolver.resolve(previous.mainBuildingId, previous.buildingId, previous.building, catalog.ids)
            ?: return (previous.mainBuildingId ?: previous.buildingId)?.takeIf { it != targetBuilding }?.let(TripStart::Building)
        val plan = loadPlan(building) ?: return TripStart.Building(building)
        val room = previous.room?.let { RoomMatcher.match(it, plan.rooms) }?.room ?: return TripStart.Building(building)
        return TripStart.Room(PlanPlace(building, room))
    }

    private suspend fun previousLesson(target: LessonDetailsArgs): LessonDetailsArgs? {
        val date = LocalDate.parse(target.date)
        val start = LocalTime.parse(target.start)
        // The cache may answer before it has read the day: wait for the day that holds the target lesson.
        val day = withTimeoutOrNull(DAY_TIMEOUT) {
            days.observe(date, date).firstOrNull { list -> list.any { day -> day.date == date && day.lessons.isNotEmpty() } }
        }?.firstOrNull { it.date == date } ?: return null
        return day.lessons
            .filter { !it.isOnline && it.end <= start }
            .mapNotNull { (it.target as? LessonTarget.Lesson)?.args }
            .filter { it.pairId != target.pairId && !(it.room.isNullOrBlank() && it.building.isNullOrBlank()) }
            .maxByOrNull { it.end }
    }

    private fun fromText(start: TripStart?): UiText = when (start) {
        is TripStart.Room -> RoomTexts.title(start.place.room)
        is TripStart.Building -> UiText.Dynamic(label(start.buildingId))
        null -> UiText.Res(MapStrings.map_route_entrance)
    }

    /** Runs [block] instead of the previous load; when it ends, the spinner goes unless a newer load has started. */
    private fun launchLoad(block: suspend () -> Unit) {
        work?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) { block() }
        work = job
        job.invokeOnCompletion { if (work === job) model.update { it.copy(loading = false) } }
        job.start()
    }

    private fun label(buildingId: Int): String = directory.all.firstOrNull { it.planBuildingId == buildingId }?.label
        ?: model.value.catalog?.building(buildingId)?.name
        ?: args.lesson?.building.orEmpty()

    private fun destination(buildingId: Int): MapDestination? =
        directory.all.firstOrNull { it.planBuildingId == buildingId }?.toMapDestination()
            ?: model.value.catalog?.building(buildingId)?.let { MapDestination(it.name, it.name, it.center.latitude, it.center.longitude) }

    private fun present(state: Model): CampusMapUiState {
        state.noPlan?.let { return it }
        val plan = state.plan ?: return state.failure?.let(CampusMapUiState::Failed) ?: CampusMapUiState.Loading
        val catalog = state.catalog ?: return CampusMapUiState.Loading
        val building = plan.building
        val selected = state.selected?.let(plan.plan::room)
        val step = state.trip?.steps?.getOrNull(state.trip.current)
        val leg = step?.leg?.takeIf { it.buildingId == building.id }
        return CampusMapUiState.Plan(
            map = MapScene(
                buildingId = building.id,
                tilesPath = plan.tilesPath,
                glyphsUrl = catalog.glyphsUrl,
                bounds = building.bounds,
                bearing = building.bearing,
                level = state.level,
                route = CampusMapGeoJson.route(leg?.route, state.level, step?.part),
                selection = CampusMapGeoJson.selection(selected),
                selectedRoomId = selected?.id,
                focus = state.focus,
                frame = step?.part?.let { part ->
                    val nodes = listOf(part.edges.first().from) + part.edges.map { it.to }
                    listOf(nodes.minOf { it.longitude }, nodes.minOf { it.latitude }, nodes.maxOf { it.longitude }, nodes.maxOf { it.latitude })
                },
            ),
            // The directory's order (Кронверкский first), not the mirror's ids.
            buildings = catalog.buildings
                .sortedBy { b -> directory.all.indexOfFirst { it.planBuildingId == b.id }.takeIf { it >= 0 } ?: Int.MAX_VALUE }
                .map { BuildingChip(it.id, label(it.id), it.id == building.id) },
            levels = building.levels.map { level ->
                LevelChip(level, UiText.Res(MapStrings.map_floor_short, listOf(level + 1)), level == state.level, level in routeLevels(state, building.id))
            },
            room = selected?.takeIf { state.trip == null }?.let { room -> roomCard(room, state) },
            trip = state.trip?.let { trip ->
                TripView(
                    from = fromText(trip.from),
                    to = trip.to?.let { RoomTexts.title(it.room) },
                    steps = trip.steps.map(PlannedStep::view),
                    current = trip.current,
                    picking = state.picking,
                    notFound = trip.to != null && trip.steps.isEmpty(),
                )
            },
            search = search(state, plan),
            notice = state.target?.takeIf { it.room == null && state.trip == null }?.let {
                UiText.Res(MapStrings.map_room_missing, listOf(it.text))
            },
            loading = state.loading,
        )
    }

    /** The floors the trip's parts in [buildingId] arrive at, as itmo-map-web marks them (stairs skip the floors between). */
    private fun routeLevels(state: Model, buildingId: Int): Set<Int> = state.trip?.steps.orEmpty()
        .mapNotNull { step -> step.leg?.takeIf { it.buildingId == buildingId }?.route }
        .distinct()
        .flatMapTo(HashSet()) { route -> route.parts.map { it.level } }

    private fun roomCard(room: MapRoom, state: Model) = RoomCard(
        roomId = room.id,
        title = RoomTexts.title(room),
        subtitle = RoomTexts.subtitle(room),
        note = state.approximate?.takeIf { it.room.id == room.id }?.let {
            UiText.Res(MapStrings.map_room_approximate, listOf(state.target?.text.orEmpty(), RoomTexts.title(room)))
        },
        icon = RoomTexts.icon(room),
    )

    private fun search(state: Model, plan: LoadedPlan): SearchView {
        val present = Place.entries.filter { place -> plan.rooms.any(place::matches) }
        val chips = present.map { PlaceChip(it, it.text, it == state.place) }
        val results = when {
            state.place != null -> RoomSearch.ofPlace(state.place, plan.rooms)
            // Picking a route end before typing: the building's entrances, as itmo-map-web offers them.
            state.picking != null && state.query.isBlank() -> RoomSearch.ofPlace(Place.ENTRANCES, plan.rooms)
            else -> RoomSearch.search(state.query, plan.rooms)
        }.take(MAX_RESULTS).map { SearchResult(it.buildingId, it.id, RoomTexts.title(it), RoomTexts.subtitle(it), RoomTexts.icon(it)) }
        val elsewhere = if (state.query.isBlank()) emptyList() else state.allRooms.orEmpty()
            .filterKeys { it != plan.building.id }
            .flatMap { (id, rooms) -> RoomSearch.search(state.query, rooms).map { it to id } }
            .take(MAX_RESULTS)
            .map { (room, id) -> SearchResult(id, room.id, RoomTexts.title(room), RoomTexts.subtitle(room, label(id)), RoomTexts.icon(room)) }
        return SearchView(state.query, chips, results, elsewhere, state.picking)
    }

    private val MapRoom.point: MapPoint get() = MapPoint(longitude, latitude)

    /** The lesson room as typed in the timetable and the plan room it matched, if any. */
    private data class Target(val text: String, val room: MapRoom?)

    /** A route from [from] (null: the target building's entrance) to [to] (null until picked). */
    private data class Trip(
        val from: TripStart?,
        val to: PlanPlace?,
        val steps: List<PlannedStep> = emptyList(),
        val plans: Map<Int, LoadedPlan> = emptyMap(),
        val current: Int = 0,
    )

    private data class Model(
        val catalog: MapCatalog? = null,
        val plan: LoadedPlan? = null,
        val level: Int = 0,
        val selected: Long? = null,
        val target: Target? = null,
        val approximate: RoomMatch? = null,
        val trip: Trip? = null,
        val picking: RouteEnd? = null,
        val loading: Boolean = false,
        val query: String = "",
        val place: Place? = null,
        val allRooms: Map<Int, List<MapRoom>>? = null,
        val focus: MapPoint? = null,
        val failure: AppError? = null,
        val noPlan: CampusMapUiState.NoPlan? = null,
    )

    companion object {
        /** The saved-state key of the [CampusMapArgs] JSON ([argsOf]). */
        const val ARG = "campus_map_args"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MAX_RESULTS = 30
        private val DAY_TIMEOUT = 2.seconds
        private val json = Json { ignoreUnknownKeys = true }

        /** The saved-state entries of a map route's [args], for the Android shell and the iOS parameters. */
        fun argsOf(args: CampusMapArgs): Map<String, String> = mapOf(ARG to json.encodeToString(CampusMapArgs.serializer(), args))

        private fun decode(text: String): CampusMapArgs = json.decodeFromString(CampusMapArgs.serializer(), text)
    }
}
