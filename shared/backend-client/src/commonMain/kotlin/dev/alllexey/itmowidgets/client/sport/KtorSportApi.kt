package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import dev.alllexey.itmowidgets.client.sport.model.FriendsSportBookingsResponse
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportQueue
import dev.alllexey.itmowidgets.client.sport.model.SportQueueEntry
import dev.alllexey.itmowidgets.client.sport.model.UserSportBookingsResponse
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer

internal class KtorSportApi(private val http: BackendHttp) : SportApi {

    override suspend fun syncSportLessons(lessonIds: List<Long>) =
        http.callUnit(BackendRoute(HttpMethod.Post, sport("sign", "sync"), body = jsonBody(lessonIds)))

    override suspend fun friendsSportBookings(): FriendsSportBookingsResponse =
        http.call(
            BackendRoute(HttpMethod.Get, sport("friends", "sport-bookings")),
            FriendsSportBookingsResponse.serializer(),
        )

    override suspend fun userSportBookings(isu: Int): UserSportBookingsResponse =
        http.call(
            BackendRoute(HttpMethod.Get, sport("users", isu.toString(), "bookings")),
            UserSportBookingsResponse.serializer(),
        )

    override suspend fun mySportFreeSignEntries(): List<SportFreeSignEntry> =
        http.call(BackendRoute(HttpMethod.Get, free("entry", "my")), ListSerializer(freeEntry))

    override suspend fun createSportFreeSignEntry(request: SportFreeSignRequest): SportFreeSignEntry =
        http.call(BackendRoute(HttpMethod.Post, free("entry", "create"), body = jsonBody(request)), freeEntry)

    override suspend fun cancelSportFreeSignEntry(id: Long) =
        http.callUnit(BackendRoute(HttpMethod.Post, free("entry", id.toString(), "cancel")))

    override suspend fun cancelSportFreeSignEntryByLesson(lessonId: Long) =
        http.callUnit(BackendRoute(HttpMethod.Post, free("lesson", lessonId.toString(), "cancel")))

    override suspend fun currentSportFreeSignQueues(): List<SportFreeSignQueue> =
        http.call(BackendRoute(HttpMethod.Get, free("queue", "current")), ListSerializer(freeQueue))

    override suspend fun markSportFreeSignEntrySatisfiedByLesson(lessonId: Long) =
        http.callUnit(BackendRoute(HttpMethod.Post, free("lesson", lessonId.toString(), "mark-satisfied")))

    override suspend fun sportAutoSignLimits(): SportAutoSignLimits =
        http.call(BackendRoute(HttpMethod.Get, auto("limits")), SportAutoSignLimits.serializer())

    override suspend fun mySportAutoSignEntries(): List<SportAutoSignEntry> =
        http.call(BackendRoute(HttpMethod.Get, auto("entry", "my")), ListSerializer(autoEntry))

    override suspend fun createSportAutoSignEntry(request: SportAutoSignRequest): SportAutoSignEntry =
        http.call(BackendRoute(HttpMethod.Post, auto("entry", "create"), body = jsonBody(request)), autoEntry)

    override suspend fun cancelSportAutoSignEntry(id: Long) =
        http.callUnit(BackendRoute(HttpMethod.Post, auto("entry", id.toString(), "cancel")))

    override suspend fun cancelSportAutoSignEntryByLesson(lessonId: Long) =
        http.callUnit(BackendRoute(HttpMethod.Post, auto("lesson", lessonId.toString(), "cancel")))

    // A read on POST: Backend keeps 1.x's method for released clients.
    override suspend fun currentSportAutoSignQueues(): List<SportAutoSignQueue> =
        http.call(BackendRoute(HttpMethod.Post, auto("queue", "current")), ListSerializer(autoQueue))

    override suspend fun markSportAutoSignEntrySatisfiedByLesson(lessonId: Long) =
        http.callUnit(BackendRoute(HttpMethod.Post, auto("lesson", lessonId.toString(), "mark-satisfied")))

    private fun sport(vararg segments: String) = listOf("api", "sport") + segments

    private fun free(vararg segments: String) = sport("free-sign", *segments)

    private fun auto(vararg segments: String) = sport("auto-sign", *segments)

    private companion object {
        val freeEntry = SubtypeSerializer(SportQueueEntry.serializer(), SportFreeSignEntry::class)
        val autoEntry = SubtypeSerializer(SportQueueEntry.serializer(), SportAutoSignEntry::class)
        val freeQueue = SubtypeSerializer(SportQueue.serializer(), SportFreeSignQueue::class)
        val autoQueue = SubtypeSerializer(SportQueue.serializer(), SportAutoSignQueue::class)
    }
}
