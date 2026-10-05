package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer

internal class KtorScheduleApi(private val http: BackendHttp) : ScheduleApi {

    override suspend fun syncLessons(request: LessonSyncRequest) =
        http.callUnit(BackendRoute(HttpMethod.Post, lessons("sync"), body = jsonBody(request)))

    override suspend fun userLessons(isu: Int, from: LocalDate, to: LocalDate): List<LessonDto> =
        http.call(
            BackendRoute(
                HttpMethod.Get,
                lessons("user", isu.toString()),
                query = listOf("from" to from.toString(), "to" to to.toString()),
            ),
            ListSerializer(LessonDto.serializer()),
        )

    override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): List<UserProfile> =
        http.call(
            BackendRoute(
                HttpMethod.Get,
                lessons(pairId.toString(), "friends"),
                query = listOf("date" to date.toString()),
            ),
            ListSerializer(UserProfile.serializer()),
        )

    private fun lessons(vararg segments: String) = listOf("api", "schedule", "lessons") + segments
}
