package dev.alllexey.itmowidgets.feature.sport.data.golden

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.core.testing.myItmoResponses
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
import java.io.IOException
import java.time.Clock
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * The sport data layer over MyItmoApi 1.x and Core 1.x, both answering from [myItmo] and [backend] by
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

    private val myItmoClient = myItmoResponses { request -> answer("my", request, myItmo) }
    private val widgets = object : ItmoWidgetsImpl(myItmoClient, "https://backend.test/") {
        override val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val (code, body) = answer("backend", chain.request(), backend)
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(code).message("Synthetic response")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            })
            .build()
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

    val score = SportScoreRepositoryImpl(myItmoClient, noOverride, time, noDemo(), dispatchers)
    val data = SportDataRepositoryImpl(friendRepository, gate, myItmoClient.api, widgets.api, score, time, noDemo(), dispatchers)
    val schedule = SportScheduleRepositoryImpl(data, myItmoClient.api, time, noTemplates, noDemo(), dispatchers)
    val bookings = SportBookingRepositoryImpl(gate, data, myItmoClient.api, widgets.api, time, noDemo(), dispatchers)
    val users = UserSportRepositoryImpl(gate, widgets.api, time, noDemo(), dispatchers)
    val actions = SportActionRepositoryImpl(gate, myItmoClient.api, widgets.api, noDemo(), dispatchers)

    private val clock = Clock.fixed(wallClock.toJavaInstant(), ZoneOffset.UTC)

    fun pushHandler(auto: Boolean) = SportSignPushHandler(
        auto, widgets.gson, actions, widgets.api,
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

    private fun answer(host: String, request: Request, answers: Map<String, String>): Pair<Int, String> {
        val body = request.body?.let { content -> Buffer().also(content::writeTo).readUtf8() }.orEmpty()
        val query = request.url.encodedQuery?.let { "?$it" }.orEmpty()
        requests += "$host ${request.method} ${request.url.encodedPath}$query $body".trimEnd()
        val answer = answers["${request.method} ${request.url.encodedPath}"] ?: return 418 to "{}"
        if (answer == NETWORK) throw IOException("Synthetic network failure")
        return status(answer)
    }

    private object NoPending : PendingSportBookingsRepository {
        override fun observePendingBookings(): Flow<AppResult<List<PendingSportBooking>>> = flowOf()
        override suspend fun getPendingBookings(): AppResult<List<PendingSportBooking>> = AppResult.Success(emptyList())
        override suspend fun refresh() = Unit
    }

    companion object {
        const val NETWORK = "network"

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
