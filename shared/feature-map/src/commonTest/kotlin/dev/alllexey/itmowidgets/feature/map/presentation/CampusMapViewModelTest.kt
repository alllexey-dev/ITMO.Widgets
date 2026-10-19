package dev.alllexey.itmowidgets.feature.map.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.location.KnownBuilding
import dev.alllexey.itmowidgets.core.navigation.CampusMapArgs
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.map.MapFixtures
import dev.alllexey.itmowidgets.feature.map.data.graph.TransitionJson
import dev.alllexey.itmowidgets.feature.map.data.rooms.RoomsJson
import dev.alllexey.itmowidgets.feature.map.domain.CampusMapRepository
import dev.alllexey.itmowidgets.feature.map.domain.catalog.LoadedPlan
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapBuilding
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapCatalog
import dev.alllexey.itmowidgets.feature.map.domain.catalog.MapPoint
import dev.alllexey.itmowidgets.feature.map.domain.rooms.MapRoom
import dev.alllexey.itmowidgets.feature.map.domain.trip.BuildingPlan
import dev.alllexey.itmowidgets.shared.feature.map.MapStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class CampusMapViewModelTest {
    private val main = TestMainDispatcher()
    private var lessonsOfTheDay = emptyList<DayLesson>()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun aLessonRoomOpensOnItsFloorSelected() = runTest(main.dispatcher) {
        val state = plan(viewModel(lesson(room = "2403")))

        assertEquals(3, state.map.level)
        assertEquals(UiText.Dynamic("2403"), state.room?.title)
        assertNull(state.notice)
        assertTrue(state.levels.single { it.selected }.level == 3)
    }

    @Test
    fun theRouteWithoutAnEarlierLessonStartsAtTheEntrance() = runTest(main.dispatcher) {
        val state = plan(viewModel(lesson(room = "2403"), route = true))

        val trip = assertNotNull(state.trip)
        assertEquals(UiText.Res(MapStrings.map_route_entrance), trip.from)
        assertEquals(UiText.Dynamic("2403"), trip.to)
        // The walk inside the entrance is trimmed, so the trip starts on the stairs.
        assertEquals(listOf(TripStepKind.STAIRS_UP, TripStepKind.FLOOR), trip.steps.map { it.kind })
        assertEquals(3, state.map.level)
        assertTrue("LineString" in state.map.route)
    }

    @Test
    fun anEarlierLessonInAnotherBuildingGoesThroughTheCity() = runTest(main.dispatcher) {
        lessonsOfTheDay = listOf(dayLesson(lesson(room = "1124", main = 273, pair = 1, start = "08:20", end = "09:50")))

        val vm = viewModel(lesson(room = "2403"), route = true)
        val trip = assertNotNull(plan(vm).trip)

        assertEquals(UiText.Dynamic("1124"), trip.from)
        assertTrue(TripStepKind.CITY in trip.steps.map { it.kind })
        assertEquals(273, plan(vm).map.buildingId)
        val city = trip.steps.indexOfFirst { it.kind == TripStepKind.CITY }
        assertEquals("Кронверкский пр., 49", trip.steps[city].destination?.label)
        vm.step(trip.steps.lastIndex)
        assertEquals(13, plan(vm).map.buildingId)
    }

    @Test
    fun fromARoomTheDestinationIsPickedBySearchInAnotherBuilding() = runTest(main.dispatcher) {
        val vm = viewModel(lesson(room = "2403"))
        collect(vm)
        vm.routeFrom()

        val picking = plan(vm)
        assertEquals(RouteEnd.TO, picking.trip?.picking)
        assertEquals(UiText.Dynamic("2403"), picking.trip?.from)
        assertNull(picking.trip?.to)
        assertTrue(picking.search.active)
        // Before anything is typed, the building's entrances.
        assertEquals(listOf(469L), picking.search.results.map { it.roomId })

        vm.search("1124")
        vm.openResult(plan(vm).search.elsewhere.single())

        val trip = assertNotNull(plan(vm).trip)
        assertNull(trip.picking)
        assertEquals(UiText.Dynamic("1124"), trip.to)
        assertTrue(TripStepKind.CITY in trip.steps.map { it.kind })
        assertEquals(13, plan(vm).map.buildingId)
        assertFalse(plan(vm).loading)
    }

    @Test
    fun swappingTurnsTheEntranceStartIntoTheEnd() = runTest(main.dispatcher) {
        val vm = viewModel(lesson(room = "2403"), route = true)
        collect(vm)
        vm.swap()

        val trip = assertNotNull(plan(vm).trip)
        assertEquals(UiText.Dynamic("2403"), trip.from)
        assertEquals(UiText.Dynamic("Вход"), trip.to)
        assertEquals(listOf(TripStepKind.STAIRS_DOWN, TripStepKind.FLOOR), trip.steps.map { it.kind })
    }

    @Test
    fun theDestinationCanBeTappedOnAnotherBuildingsPlan() = runTest(main.dispatcher) {
        val vm = viewModel(lesson(room = "2403"))
        collect(vm)
        vm.routeFrom()
        vm.selectBuilding(273)
        assertEquals(RouteEnd.TO, plan(vm).trip?.picking)

        vm.selectRoom(1124)

        val trip = assertNotNull(plan(vm).trip)
        assertEquals(UiText.Dynamic("1124"), trip.to)
        assertTrue(TripStepKind.CITY in trip.steps.map { it.kind })
    }

    @Test
    fun aTapOnThePlanPicksTheStart() = runTest(main.dispatcher) {
        val vm = viewModel(lesson(room = "2403"), route = true)
        collect(vm)
        vm.pick(RouteEnd.FROM)
        assertEquals(RouteEnd.FROM, plan(vm).search.picking)

        vm.selectRoom(77)

        val trip = assertNotNull(plan(vm).trip)
        assertNull(trip.picking)
        assertEquals(UiText.Res(MapStrings.map_place_restrooms), trip.from)
        assertEquals(UiText.Dynamic("2403"), trip.to)
    }

    @Test
    fun aRoomMissingFromThePlanSaysSo() = runTest(main.dispatcher) {
        val state = plan(viewModel(lesson(room = "9999")))

        assertNull(state.room)
        assertEquals(UiText.Res(MapStrings.map_room_missing, listOf("9999")), state.notice)
        assertEquals(0, state.map.level)
    }

    @Test
    fun aBuildingWithoutAPlanOffersMaps() = runTest(main.dispatcher) {
        val state = viewModel(lesson(room = "1784", main = 5)).let { vm -> collect(vm); vm.uiState.value }

        val noPlan = assertIs<CampusMapUiState.NoPlan>(state)
        assertEquals("Вяземский пер., 5-7", noPlan.building)
        assertEquals(59.97, noPlan.destination?.latitude)
    }

    @Test
    fun searchFindsTheShownBuildingFirstThenTheOthers() = runTest(main.dispatcher) {
        val vm = viewModel(null)
        collect(vm)
        vm.search("11")

        val search = plan(vm).search
        assertTrue(search.results.isEmpty())
        assertEquals(listOf(273 to 1124L), search.elsewhere.map { it.buildingId to it.roomId })

        vm.openResult(search.elsewhere.single())
        assertEquals(273, plan(vm).map.buildingId)
        assertEquals(1124L, plan(vm).room?.roomId)
    }

    @Test
    fun placeChipsListOnlyWhatTheBuildingHas() = runTest(main.dispatcher) {
        val vm = viewModel(null)
        collect(vm)

        assertEquals(listOf(Place.RESTROOMS, Place.ENTRANCES), plan(vm).search.places.map { it.place })
        vm.selectPlace(Place.RESTROOMS)
        assertEquals(listOf(77L), plan(vm).search.results.map { it.roomId })
    }

    @Test
    fun noMirrorIsAnError() = runTest(main.dispatcher) {
        val vm = CampusMapViewModel(SavedStateHandle(), FakeRepository(offline = true), DIRECTORY, gateway(), dispatchers())
        collect(vm)

        assertEquals(CampusMapUiState.Failed(AppError.Network), vm.uiState.value)
    }

    private fun kotlinx.coroutines.test.TestScope.collect(vm: CampusMapViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()
    }

    private fun kotlinx.coroutines.test.TestScope.plan(vm: CampusMapViewModel): CampusMapUiState.Plan {
        collect(vm)
        return assertIs<CampusMapUiState.Plan>(vm.uiState.value)
    }

    private fun viewModel(lesson: LessonDetailsArgs?, route: Boolean = false): CampusMapViewModel {
        val handle = SavedStateHandle(if (lesson == null) emptyMap() else CampusMapViewModel.argsOf(CampusMapArgs(lesson, route)))
        return CampusMapViewModel(handle, FakeRepository(), DIRECTORY, gateway(), dispatchers())
    }

    private fun dispatchers() = AppDispatchers(main.dispatcher, main.dispatcher, main.dispatcher)

    private fun gateway() = object : DayLessonsGateway {
        override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
            flowOf(listOf(LessonDay(from, lessonsOfTheDay, cachedAt = null)))

        override suspend fun refresh(from: LocalDate, to: LocalDate) = AppResult.Success(Unit)
    }

    private class FakeRepository(private val offline: Boolean = false) : CampusMapRepository {
        override suspend fun catalog(): AppResult<MapCatalog> =
            if (offline) AppResult.Failure(AppError.Network) else AppResult.Success(CATALOG)

        override suspend fun plan(buildingId: Int): AppResult<LoadedPlan> {
            val building = CATALOG.building(buildingId) ?: return AppResult.Failure(AppError.NotFound)
            return AppResult.Success(LoadedPlan(building, "/tiles/$buildingId.mbtiles", PLANS.getValue(buildingId)))
        }

        override suspend fun allRooms(): AppResult<Map<Int, List<MapRoom>>> = AppResult.Success(PLANS.mapValues { it.value.rooms })
    }

    private companion object {
        val CATALOG = MapCatalog(
            listOf(
                MapBuilding(13, "Кронверкский пр., 49", listOf(30.307, 59.955, 30.311, 59.957), MapPoint(30.3095, 59.9567), 160.0, listOf(0, 1, 2, 3, 4)),
                MapBuilding(273, "Ломоносова ул., 9", listOf(30.336, 59.925, 30.339, 59.927), MapPoint(30.338, 59.9266), 216.0, listOf(0, 1)),
            ),
            attribution = "План: Университет ИТМО",
            glyphsUrl = "https://map.example/glyphs/{fontstack}/{range}.pbf",
        )
        val PLANS = mapOf(
            13 to BuildingPlan(13, RoomsJson.decode(13, MapFixtures.ROOMS_13), TransitionJson.decode(MapFixtures.GRAPH_13)),
            273 to BuildingPlan(273, RoomsJson.decode(273, MapFixtures.ROOMS_273), TransitionJson.decode(MapFixtures.GRAPH_273)),
        )
        val DIRECTORY = BuildingDirectory(
            listOf(
                KnownBuilding("kronva", listOf(13), listOf("кронв"), "Кронверкский пр., 49", "Кронверкский проспект, 49", 59.9567, 30.3087, planBuildingId = 13),
                KnownBuilding("lomo", listOf(273), listOf("ломо"), "ул. Ломоносова, 9", "улица Ломоносова, 9", 59.9267, 30.3380, planBuildingId = 273),
                KnownBuilding("vyazma", listOf(5), listOf("вязем"), "Вяземский пер., 5-7", "Вяземский переулок, 5-7", 59.97, 30.30),
            ),
        )

        fun lesson(room: String, main: Int = 13, pair: Long = 9, start: String = "10:00", end: String = "11:30") = LessonDetailsArgs(
            pairId = pair, date = "2026-10-06", subjectName = "Физика", typeId = 1, format = "Очно",
            start = start, end = end, teacherFio = null, teacherIsu = null, room = room,
            building = null, buildingId = main, mainBuildingId = main, note = null,
            zoomUrl = null, zoomPassword = null, zoomInfo = null,
        )

        fun dayLesson(args: LessonDetailsArgs) = DayLesson(
            start = LocalTime.parse(args.start), end = LocalTime.parse(args.end), subject = args.subjectName, typeId = args.typeId,
            teacher = null, room = args.room, building = args.building, online = false, target = LessonTarget.Lesson(args),
        )
    }
}
