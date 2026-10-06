package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.client.common.GroupData
import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.schedule.LessonDto
import dev.alllexey.itmowidgets.client.schedule.LessonSyncRequest
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LessonFriendsRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val date = LocalDate(2026, 9, 8)

    @Test
    fun `without the opt-in nothing is requested`() = runTest {
        val api = FakeLessonFriendsApi()
        val repository = LessonFriendsRepositoryImpl(services(enabled = false), api, noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.friendsOnLesson(42, date))
        assertEquals(0, api.calls)
    }

    @Test
    fun `profiles become trimmed summaries with viewer scoped sharing in server order`() = runTest {
        val api = FakeLessonFriendsApi().apply {
            result = { pairId, requestedDate ->
                assertEquals(42L, pairId); assertEquals(date, requestedDate)
                listOf(
                    profile(300003, " Второй ", UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = false)),
                    profile(200002, "Первый", UserCapabilities(canViewSchedule = true, canViewSport = true, canViewFriends = true))
                )
            }
        }
        val repository = LessonFriendsRepositoryImpl(services(enabled = true), api, noDemo(), dispatchers = dispatchers)

        val friends = (repository.friendsOnLesson(42, date) as AppResult.Success).value

        assertEquals(listOf(300003, 200002), friends.map { it.isu })
        assertEquals(
            UserSummary(
                300003, "Второй", null, listOf(UserGroup("M3100", 1, "ФТМИ")),
                UserSharing(sport = false, schedule = true, friends = false)
            ),
            friends.first()
        )
        assertEquals(1, api.calls)
    }

    @Test
    fun `the request reaches the Core 2_0 route`() = runTest {
        val harness = Core2Harness(session()) { respondJson("""{"success":true,"data":[],"error":null}""") }
        val repository = LessonFriendsRepositoryImpl(services(true), harness.client.schedule, noDemo(), dispatchers)

        assertEquals(AppResult.Success(emptyList<UserSummary>()), repository.friendsOnLesson(42, date))
        val request = harness.backendRequests.single()
        assertEquals("/api/schedule/lessons/42/friends", request.url.encodedPath)
        assertEquals("2026-09-08", request.url.parameters["date"])
    }

    @Test
    fun `transport failures, denials and empty envelopes are errors, not empty lists`() = runTest {
        suspend fun answer(respond: suspend MockRequestHandleScope.() -> HttpResponseData): AppResult<List<UserSummary>> {
            val harness = Core2Harness(session()) { respond() }
            return LessonFriendsRepositoryImpl(services(true), harness.client.schedule, noDemo(), dispatchers).friendsOnLesson(42, date)
        }

        assertEquals(AppResult.Failure(AppError.Network), answer { throw IOException("offline") })
        assertEquals(AppResult.Failure(AppError.Unauthorized), answer { respondJson(errorEnvelope("unauthorized"), HttpStatusCode.Unauthorized) })
        val empty = answer { respondJson("""{"success":true,"data":null,"error":null}""") }
        assertTrue((empty as AppResult.Failure).error is AppError.Unknown)
    }

    private fun profile(isu: Int, name: String, capabilities: UserCapabilities) = UserProfile(
        UserData(isu, name, null, listOf(GroupData("M3100", 1, "ФТМИ")), capabilities),
        RelationshipState.FRIENDS
    )

    private fun services(enabled: Boolean) = FakeBackendGate(enabled)

    /** Only the lesson-friends call is answered; anything else is a test bug. */
    private class FakeLessonFriendsApi : ScheduleApi {
        var result: (Long, LocalDate) -> List<UserProfile> = { _, _ -> emptyList() }
        var calls = 0
            private set

        override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): List<UserProfile> {
            calls += 1
            return result(pairId, date)
        }

        override suspend fun syncLessons(request: LessonSyncRequest) = error("Unexpected syncLessons")

        override suspend fun userLessons(isu: Int, from: LocalDate, to: LocalDate): List<LessonDto> = error("Unexpected userLessons")
    }
}
