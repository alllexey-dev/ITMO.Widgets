package dev.alllexey.itmowidgets.feature.sport.data.demo

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverrideSource
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoSportSlots
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.data.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.sport.data.unreachable
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.UserSportRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import io.ktor.client.engine.mock.MockEngine
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The demo session's sport: every read answers from the demo set, every action is refused, nothing is requested. */
class SportDemoGateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val demo = FakeDemoMode(active = true)
    private val time = FixedAcademicTime()
    // The stored opt-in is on: the demo alone must keep every request local.
    private val backend = FakeBackendGate(optedIn = true, demo)
    private val myItmo = unreachableMyItmoClient()
    private val sportApi = unreachable<SportApi>()
    private val score = SportScoreRepositoryImpl(myItmo, NoOverride, time, demo, dispatchers)
    private val data = SportDataRepositoryImpl(NoFriends, backend, myItmo, sportApi, score, time, demo, dispatchers)

    @Test
    fun `the catalog merges the demo queues and friends without a request`() = runTest {
        val schedule = SportScheduleRepositoryImpl(data, myItmo, time, NoTemplates, demo, dispatchers)

        schedule.refreshSportSchedule()
        data.refreshSportQueueEntries()
        data.refreshSportQueues()
        data.refreshFriendsBookings()
        val lessons = schedule.observeSportSchedule().first().valueOrNull().orEmpty()

        assertTrue(lessons.any { it.signed && it.sectionName.raw == DemoSportSlots.ANNA_WEEKLY.section })
        assertTrue(lessons.any { it.isLessonReal && it.available == 0 && it.signEntry is SportFreeSignEntry })
        assertTrue(lessons.any { !it.isLessonReal && it.signEntry is SportAutoSignEntry })
        assertTrue(lessons.any { lesson -> lesson.friendsBookings.any { it.friend.isu == DemoPeople.IVAN.isu } })
        assertTrue(lessons.all { it.start.toLocalDateTime(time.timeZone).date >= time.today() })
    }

    @Test
    fun `own bookings, points and limits come from the demo set`() = runTest {
        val bookings = SportBookingRepositoryImpl(backend, data, myItmo, sportApi, time, demo, dispatchers)

        bookings.refreshSportBookings()
        data.refreshSportScore()
        data.refreshSportAttempts()
        data.refreshSportAutoSignLimits()

        val confirmed = (bookings.observeConfirmedSportBookings().first() as AppResult.Success).value
        assertTrue(confirmed.isNotEmpty() && confirmed.all { it.signed })
        val points = (data.observeSportScore().first() as LoadState.Content).value
        assertTrue(points.total in 1 until 100)
        assertTrue(data.observeSportAttempts().first() is LoadState.Content)
        assertTrue(data.observeSportAutoSignLimits().first() is LoadState.Content)
        val periods = (score.getScorePeriods() as AppResult.Success).value
        assertEquals(1, periods.count { it.current })
        assertEquals(points.summary, (score.getScoreSummary(periods.first { it.current }.id) as AppResult.Success).value)
    }

    @Test
    fun `sign-ups and queues are refused`() = runTest {
        val actions = SportActionRepositoryImpl(backend, myItmo, sportApi, demo, dispatchers)
        val refused = AppResult.Failure(AppError.DemoUnavailable)

        assertTrue(actions.areCommunityServicesEnabled())
        assertEquals(refused, actions.signIn(1))
        assertEquals(refused, actions.signOut(1))
        assertEquals(refused, actions.createFreeSignEntry(1, forceSign = false))
        assertEquals(refused, actions.cancelFreeSignEntry(1))
        assertEquals(refused, actions.createAutoSignEntry(1))
        assertEquals(refused, actions.cancelAutoSignEntry(1))
    }

    @Test
    fun `a friend's sport comes from the demo set`() = runTest {
        val users = UserSportRepositoryImpl(backend, sportApi, time, demo, dispatchers)

        val polina = (users.getUserBookings(DemoPeople.POLINA.isu) as AppResult.Success).value
        val stranger = users.getUserBookings(DemoPeople.ME_ISU + 5000)

        assertEquals(1, polina.pending.size)
        assertEquals(AppResult.Failure(AppError.Forbidden), stranger)
    }

    /** MyItmoApi 2.x whose session storage and engine both fail the test, as in `DemoNetworkGateTest`. */
    private fun unreachableMyItmoClient(): MyItmoClient = MyItmoClientFactory.create(
        storage = object : TokenStorage {
            override suspend fun read(): TokenSet? = throw AssertionError("The demo session read the ITMO session")

            override suspend fun write(tokens: TokenSet?) = throw AssertionError("The demo session wrote the ITMO session")
        },
        engine = MockEngine { request -> throw AssertionError("The demo session asked ${request.url.host}${request.url.encodedPath}") },
        clock = Clock.System
    )

    private object NoFriends : FriendRepository {
        override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = flowOf(LoadState.Disabled)
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary>? = null
    }

    private object NoOverride : SportScoreOverrideSource {
        override fun getOverride() = null
    }

    private object NoTemplates : SportLessonTemplateProvider {
        override fun getSchedule() = null
    }
}
