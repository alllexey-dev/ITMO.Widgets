package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.data.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.sport.model.QueueEntryStatus
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.feature.sport.data.Core2Harness
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportQueueSessionDataTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test
    fun `session clear replaces personal queue replay for existing and new subscribers`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)

        fixture.repository.refreshSportQueueEntries()
        assertEquals(listOf(1L), states.single().valueOrNull()?.map { it.id })

        fixture.repository.clearSessionData()

        assertEquals(2, states.size)
        assertEquals(LoadState.Disabled, states.last())
        assertEquals(LoadState.Disabled, fixture.repository.observeSportQueueEntries().first())
    }

    @Test
    fun `success from an old in flight request cannot republish cleared personal queues`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)
        val response = fixture.api.delayAutoSignResponse()
        val refresh = launch { fixture.repository.refreshSportQueueEntries() }
        response.entered.await()

        fixture.repository.clearSessionData()
        response.result.complete(listOf(autoSignEntry(1)))
        refresh.join()

        assertEquals(listOf(LoadState.Disabled), states)
        assertEquals(LoadState.Disabled, fixture.repository.observeSportQueueEntries().first())
    }

    @Test
    fun `error from an old in flight request cannot replace cleared queue state`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)
        val response = fixture.api.delayAutoSignResponse()
        val refresh = launch { fixture.repository.refreshSportQueueEntries() }
        response.entered.await()

        fixture.repository.clearSessionData()
        response.result.completeExceptionally(IOException("Old session request failed"))
        refresh.join()

        assertEquals(listOf(LoadState.Disabled), states)
        assertEquals(LoadState.Disabled, fixture.repository.observeSportQueueEntries().first())
    }

    @Test
    fun `new session refresh publishes while old response remains unable to overwrite it`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)
        val oldResponse = fixture.api.delayAutoSignResponse()
        val oldRefresh = launch { fixture.repository.refreshSportQueueEntries() }
        oldResponse.entered.await()

        fixture.repository.clearSessionData()
        fixture.api.autoSignResponse = { listOf(autoSignEntry(2)) }
        fixture.repository.refreshSportQueueEntries()
        assertEquals(listOf(2L), fixture.repository.observeSportQueueEntries().first().valueOrNull()?.map { it.id })

        oldResponse.result.complete(listOf(autoSignEntry(1)))
        oldRefresh.join()

        assertEquals(2, states.size)
        assertEquals(LoadState.Disabled, states.first())
        assertEquals(listOf(2L), states.last().valueOrNull()?.map { it.id })
        assertEquals(listOf(2L), fixture.repository.observeSportQueueEntries().first().valueOrNull()?.map { it.id })
        assertEquals(2, fixture.api.autoSignCalls.get())
        assertEquals(2, fixture.api.freeSignCalls.get())
    }

    @Test
    fun `session clear forgets the previous session's points and attempts, failures included`() = runTest {
        val fixture = createFixture()
        fixture.repository.refreshSportScore()
        fixture.repository.refreshSportAttempts()
        assertTrue(fixture.repository.observeSportScore().first() is LoadState.Error)
        assertTrue(fixture.repository.observeSportAttempts().first() is LoadState.Error)

        fixture.repository.clearSessionData()

        assertEquals(LoadState.Loading, fixture.repository.observeSportScore().first())
        assertEquals(LoadState.Loading, fixture.repository.observeSportAttempts().first())
    }

    @Test
    fun `disabled services replace previous success without requesting either queue endpoint`() = runTest {
        val fixture = createFixture()
        fixture.repository.refreshSportQueueEntries()
        assertEquals(listOf(1L), fixture.repository.observeSportQueueEntries().first().valueOrNull()?.map { it.id })
        fixture.gate.optedIn.value = false

        fixture.repository.refreshSportQueueEntries()

        assertEquals(LoadState.Disabled, fixture.repository.observeSportQueueEntries().first())
        assertEquals(1, fixture.api.freeSignCalls.get())
        assertEquals(1, fixture.api.autoSignCalls.get())
    }

    private fun TestScope.collectStates(
        repository: SportDataRepositoryImpl
    ): MutableList<LoadState<List<SportQueueEntry>>> {
        val states = mutableListOf<LoadState<List<SportQueueEntry>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeSportQueueEntries().toList(states)
        }
        return states
    }

    private suspend fun createFixture(): Fixture {
        val gate = FakeBackendGate(optedIn = true)
        val api = QueueApi()
        val myItmo = Core2Harness(Core2Harness.session()) { error("Queue refresh must not request MyITMO") }.myItmo
        val friends = object : FriendRepository {
            override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = error("Friend list is unrelated to personal queues")
            override fun observeCurrentUser(): Flow<UserSummary?> = error("Current user is unrelated to personal queues")
            override suspend fun refreshFriendList() = error("Personal queue refresh must not refresh friends")
            override val currentFriends: List<UserSummary>? = null
        }
        val repository = SportDataRepositoryImpl(
            friendRepository = friends,
            backend = gate,
            myItmo = myItmo,
            sportApi = api.instance,
            scoreRepository = SportScoreRepositoryImpl(myItmo, { null }, FixedAcademicTime(), noDemo(), dispatchers),
            time = FixedAcademicTime(),
            demo = noDemo(),
            dispatchers = dispatchers
        )
        return Fixture(gate, api, repository)
    }

    private data class Fixture(
        val gate: FakeBackendGate,
        val api: QueueApi,
        val repository: SportDataRepositoryImpl
    )

    private class DeferredResponse {
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<List<SportAutoSignEntry>>()
    }

    private class QueueApi {
        val freeSignCalls = AtomicInteger()
        val autoSignCalls = AtomicInteger()
        var autoSignResponse: suspend () -> List<SportAutoSignEntry> = {
            listOf(autoSignEntry(1))
        }

        fun delayAutoSignResponse(): DeferredResponse = DeferredResponse().also { response ->
            autoSignResponse = {
                response.entered.complete(Unit)
                response.result.await()
            }
        }

        val instance: SportApi = Proxy.newProxyInstance(
            SportApi::class.java.classLoader,
            arrayOf(SportApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "mySportFreeSignEntries" -> respond(arguments) {
                    freeSignCalls.incrementAndGet()
                    emptyList<SportFreeSignEntry>()
                }
                "mySportAutoSignEntries" -> respond(arguments) {
                    autoSignCalls.incrementAndGet()
                    autoSignResponse()
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "QueueApi"
                else -> error("Unexpected SportApi call: ${method.name}")
            }
        } as SportApi

        @Suppress("UNCHECKED_CAST")
        private fun respond(arguments: Array<out Any?>?, response: suspend () -> Any?): Any? =
            response.startCoroutineUninterceptedOrReturn(arguments!!.last() as Continuation<Any?>)
    }

    private companion object {
        val START: Instant = Instant.parse("2026-09-08T12:00:00+03:00")

        fun autoSignEntry(id: Long) = SportAutoSignEntry(
            id = id,
            prototypeLessonId = id + 100,
            realLessonId = null,
            position = 1,
            total = 1,
            isCancelled = false,
            status = QueueEntryStatus.WAITING,
            createdAt = START - 1.days,
            firstNotifiedAt = null,
            lastNotifiedAt = null,
            cancelledAt = null,
            satisfiedAt = null,
            expiredAt = null,
            notificationAttempts = 0,
            maxNotificationAttempts = 3,
            targetLesson = SportLessonDto(
                id = id + 100,
                sectionId = 1,
                sectionName = "Плавание",
                sectionLevel = 1,
                level = 1,
                typeId = 1,
                buildingId = 1,
                roomName = "Бассейн",
                start = START,
                end = START + 1.hours,
                timeSlotId = 1,
                teacherIsu = 123456,
                teacherFio = "Тестовый преподаватель"
            ),
            realLesson = null
        )
    }
}
