package dev.alllexey.itmowidgets.feature.sport.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportLessonDto
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportQueueSessionDataTest {

    @Test
    fun `session clear replaces personal queue replay for existing and new subscribers`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)

        fixture.repository.refreshSportQueueEntries()
        assertEquals(listOf(1L), states.single().dataOrNull()?.map { it.id })

        fixture.repository.clearSessionData()

        assertEquals(2, states.size)
        assertEquals(CustomDataState.Disabled, states.last())
        assertEquals(CustomDataState.Disabled, fixture.repository.observeSportQueueEntries().first())
    }

    @Test
    fun `success from an old in flight request cannot republish cleared personal queues`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)
        val response = fixture.api.delayAutoSignResponse()
        val refresh = launch { fixture.repository.refreshSportQueueEntries() }
        response.entered.await()

        fixture.repository.clearSessionData()
        response.result.complete(ApiResponse.success(listOf(autoSignEntry(1))))
        refresh.join()

        assertEquals(listOf(CustomDataState.Disabled), states)
        assertEquals(CustomDataState.Disabled, fixture.repository.observeSportQueueEntries().first())
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

        assertEquals(listOf(CustomDataState.Disabled), states)
        assertEquals(CustomDataState.Disabled, fixture.repository.observeSportQueueEntries().first())
    }

    @Test
    fun `new session refresh publishes while old response remains unable to overwrite it`() = runTest {
        val fixture = createFixture()
        val states = collectStates(fixture.repository)
        val oldResponse = fixture.api.delayAutoSignResponse()
        val oldRefresh = launch { fixture.repository.refreshSportQueueEntries() }
        oldResponse.entered.await()

        fixture.repository.clearSessionData()
        fixture.api.autoSignResponse = { ApiResponse.success(listOf(autoSignEntry(2))) }
        fixture.repository.refreshSportQueueEntries()
        assertEquals(listOf(2L), fixture.repository.observeSportQueueEntries().first().dataOrNull()?.map { it.id })

        oldResponse.result.complete(ApiResponse.success(listOf(autoSignEntry(1))))
        oldRefresh.join()

        assertEquals(2, states.size)
        assertEquals(CustomDataState.Disabled, states.first())
        assertEquals(listOf(2L), states.last().dataOrNull()?.map { it.id })
        assertEquals(listOf(2L), fixture.repository.observeSportQueueEntries().first().dataOrNull()?.map { it.id })
        assertEquals(2, fixture.api.autoSignCalls.get())
        assertEquals(2, fixture.api.freeSignCalls.get())
    }

    @Test
    fun `disabled services replace previous success without requesting either queue endpoint`() = runTest {
        val fixture = createFixture()
        fixture.repository.refreshSportQueueEntries()
        assertEquals(listOf(1L), fixture.repository.observeSportQueueEntries().first().dataOrNull()?.map { it.id })
        fixture.settings.setCustomServicesEnabled(false)

        fixture.repository.refreshSportQueueEntries()

        assertEquals(CustomDataState.Disabled, fixture.repository.observeSportQueueEntries().first())
        assertEquals(1, fixture.api.freeSignCalls.get())
        assertEquals(1, fixture.api.autoSignCalls.get())
    }

    private fun TestScope.collectStates(
        repository: SportDataRepositoryImpl
    ): MutableList<CustomDataState<List<SportQueueEntry>>> {
        val states = mutableListOf<CustomDataState<List<SportQueueEntry>>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeSportQueueEntries().toList(states)
        }
        return states
    }

    private suspend fun createFixture(): Fixture {
        val settings = AppSettingsStorage(InMemoryPreferencesDataStore())
        settings.setCustomServicesEnabled(true)
        val api = QueueApi()
        val myItmo = myItmoStub { error("Queue refresh must not request MyITMO") }
        val friends = object : FriendRepository {
            override fun observeFriendList(): Flow<FriendListState> = error("Friend list is unrelated to personal queues")
            override fun observeCurrentUser(): Flow<UserSummary?> = error("Current user is unrelated to personal queues")
            override suspend fun refreshFriendList() = error("Personal queue refresh must not refresh friends")
            override val currentFriends: List<UserSummary>? = null
        }
        val repository = SportDataRepositoryImpl(
            friendRepository = friends,
            settings = settings,
            myItmoApi = myItmo.api,
            widgetsApi = api.instance,
            scoreRepository = SportScoreRepositoryImpl(myItmo, object : SportScoreOverrideProvider {
                override fun getOverride() = null
            })
        )
        return Fixture(settings, api, repository)
    }

    private data class Fixture(
        val settings: AppSettingsStorage,
        val api: QueueApi,
        val repository: SportDataRepositoryImpl
    )

    private class DeferredResponse {
        val entered = CompletableDeferred<Unit>()
        val result = CompletableDeferred<ApiResponse<List<SportAutoSignEntry>>>()
    }

    private class QueueApi {
        val freeSignCalls = AtomicInteger()
        val autoSignCalls = AtomicInteger()
        var autoSignResponse: suspend () -> ApiResponse<List<SportAutoSignEntry>> = {
            ApiResponse.success(listOf(autoSignEntry(1)))
        }

        fun delayAutoSignResponse(): DeferredResponse = DeferredResponse().also { response ->
            autoSignResponse = {
                response.entered.complete(Unit)
                response.result.await()
            }
        }

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "mySportFreeSignEntries" -> respond(arguments) {
                    freeSignCalls.incrementAndGet()
                    ApiResponse.success(emptyList<SportFreeSignEntry>())
                }
                "mySportAutoSignEntries" -> respond(arguments) {
                    autoSignCalls.incrementAndGet()
                    autoSignResponse()
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "QueueApi"
                else -> error("Unexpected ItmoWidgetsApi call: ${method.name}")
            }
        } as ItmoWidgetsApi

        @Suppress("UNCHECKED_CAST")
        private fun respond(arguments: Array<out Any?>?, response: suspend () -> Any?): Any? =
            response.startCoroutineUninterceptedOrReturn(arguments!!.last() as Continuation<Any?>)
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private companion object {
        val START: OffsetDateTime = OffsetDateTime.parse("2026-09-08T12:00:00+03:00")

        fun autoSignEntry(id: Long) = SportAutoSignEntry(
            id = id,
            prototypeLessonId = id + 100,
            realLessonId = null,
            position = 1,
            total = 1,
            isCancelled = false,
            status = QueueEntryStatus.WAITING,
            createdAt = START.minusDays(1),
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
                end = START.plusHours(1),
                timeSlotId = 1,
                teacherIsu = 123456,
                teacherFio = "Тестовый преподаватель"
            ),
            realLesson = null
        )
    }
}
