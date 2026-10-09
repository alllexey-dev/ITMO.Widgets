package dev.alllexey.itmowidgets.client

import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.app.KtorAppApi
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.device.KtorDeviceApi
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.friends.KtorFriendsApi
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.links.KtorSubjectLinksApi
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.client.reviews.KtorTeacherReviewsApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.schedule.KtorScheduleApi
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.client.sport.KtorSportApi
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.users.KtorUsersApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import io.ktor.client.engine.HttpClientEngine

/**
 * Core 2.0: the typed client of ITMO.Widgets Backend, one API per area.
 *
 * [baseUrl] is the Backend origin (`https://widgets.alllexey.dev` or `https://dev.widgets.alllexey.dev`); route
 * paths are absolute, so a path in [baseUrl] is replaced, as in Retrofit. [tokens] is asked once per request.
 * [engine] belongs to the caller (OkHttp on Android, Darwin on iOS) and is not closed by the client. [version] is sent
 * with every request as `X-App-Version` ([ClientVersion]); `null` (tests, the default) sends no such header.
 *
 * Every call fails only with [dev.alllexey.itmowidgets.client.error.BackendException] or a cancellation. The client
 * checks neither `DemoMode` nor the custom-services opt-in: its holders do.
 */
class BackendClient(
    baseUrl: String,
    tokens: AccessTokenSource,
    engine: HttpClientEngine,
    version: ClientVersion? = null,
) {
    private val http = BackendHttp(baseUrl, tokens, engine, version)

    val users: UsersApi = KtorUsersApi(http)
    val friends: FriendsApi = KtorFriendsApi(http)
    val sport: SportApi = KtorSportApi(http)
    val links: SubjectLinksApi = KtorSubjectLinksApi(http)
    val reviews: TeacherReviewsApi = KtorTeacherReviewsApi(http)
    val device: DeviceApi = KtorDeviceApi(http)
    val app: AppApi = KtorAppApi(http)
    val schedule: ScheduleApi = KtorScheduleApi(http)
}
