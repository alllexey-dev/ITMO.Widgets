package dev.alllexey.itmowidgets.feature.sport.data.golden

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushHandler
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportActionRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.UserSportRepositoryImpl
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The sport data layer over MyItmoApi 2.x and Core 2.0 on one MockEngine ([Core2Harness]), my.itmo.ru and Backend
 * answering from [myItmo] and [backend] by
 * `"<METHOD> <path>"` (see [status]); [NETWORK] fails the request before any answer. Every request lands in
 * [requests].
 */
class SportGoldenHarness(
    dispatchers: AppDispatchers,
    time: AcademicTimeProvider,
    wallClock: Instant,
    friends: List<UserSummary> = emptyList()
) {
    val myItmo = mutableMapOf<String, String>()
    val backend = mutableMapOf<String, String>()
    val requests = mutableListOf<String>()
    val notifications = mutableListOf<String>()
    val gate = FakeBackendGate(optedIn = true)

    private val clients = Core2Harness(Core2Harness.session()) { request ->
        when (request.url.host) {
            MY_ITMO_HOST -> answer("my", request, myItmo)
            else -> answer("backend", request, backend)
        }
    }

    private val friendRepository = object : FriendRepository {
        override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> = flowOf(LoadState.Content(friends))
        override fun observeCurrentUser(): Flow<UserSummary?> = flowOf(null)
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary> = friends
    }

    private val noOverride = object : SportScoreOverrideProvider {
        override fun getOverride() = null
    }

    private val noTemplates = object : SportLessonTemplateProvider {
        override fun getSchedule() = null
    }

    private val sportApi = clients.client.sport

    val score = SportScoreRepositoryImpl(clients.myItmo, noOverride, time, noDemo(), dispatchers)
    val data = SportDataRepositoryImpl(friendRepository, gate, clients.myItmo, sportApi, score, time, noDemo(), dispatchers)
    val schedule = SportScheduleRepositoryImpl(data, clients.myItmo, time, noTemplates, noDemo(), dispatchers)
    val bookings = SportBookingRepositoryImpl(gate, data, clients.myItmo, sportApi, time, noDemo(), dispatchers)
    val users = UserSportRepositoryImpl(gate, sportApi, time, noDemo(), dispatchers)
    val actions = SportActionRepositoryImpl(gate, clients.myItmo, sportApi, noDemo(), dispatchers)

    private val clock = FakeClock(wallClock)

    fun pushHandler(auto: Boolean) = SportSignPushHandler(
        auto, actions, sportApi,
        bookings, NoPending, ScheduleWidgetRefreshRequester { requests += "widgets refresh" },
        object : AppNotifier {
            override fun show(notification: AppNotification) {
                notifications += renderNotification(notification)
            }

            override fun cancel(channel: String, id: Int) = Unit
            override fun clear() = Unit
        },
        clock, RecordingDiagnostics(), gate, noDemo()
    )

    private fun MockRequestHandleScope.answer(
        host: String,
        request: HttpRequestData,
        answers: Map<String, String>
    ): HttpResponseData {
        val query = request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        requests += "$host ${request.method.value} ${request.url.encodedPath}$query ${request.bodyText()}".trimEnd()
        val answer = answers["${request.method.value} ${request.url.encodedPath}"]
            ?: return respondJson("{}", HttpStatusCode(418, "Unexpected request"))
        if (answer == NETWORK) throw IOException("Synthetic network failure")
        val (status, body) = status(answer)
        return respondJson(body, HttpStatusCode.fromValue(status))
    }

    private object NoPending : PendingSportBookingsRepository {
        override fun observePendingBookings(): Flow<AppResult<List<PendingSportBooking>>> = flowOf()
        override suspend fun getPendingBookings(): AppResult<List<PendingSportBooking>> = AppResult.Success(emptyList())
        override suspend fun refresh() = Unit
    }

    companion object {
        const val NETWORK = "network"
        const val MY_ITMO_HOST = "my.itmo.ru"

        /** `"<status>|<body>"` answers with that status, any other answer is HTTP 200. */
        fun status(answer: String): Pair<Int, String> {
            val status = answer.substringBefore('|', "").toIntOrNull() ?: return 200 to answer
            return status to answer.substringAfter('|')
        }

        /** The notification without resource IDs, which change with every resource edit. */
        fun renderNotification(notification: AppNotification): String {
            val title = when ((notification.title as UiText.Resource).resourceId) {
                R.string.notification_sport_success -> "success"
                R.string.notification_sport_failure -> "failure"
                else -> "other"
            }
            return "$title ${(notification.text as UiText.Resource).arguments} ${notification.channel} ${notification.destination}"
        }
    }
}
