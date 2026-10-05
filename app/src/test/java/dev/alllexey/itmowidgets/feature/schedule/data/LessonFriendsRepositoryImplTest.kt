package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.GroupData
import dev.alllexey.itmowidgets.core.model.UserCapabilities
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.social.RelationshipState
import dev.alllexey.itmowidgets.core.model.social.UserProfile
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.junit.Assert.assertEquals
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
        val repository = LessonFriendsRepositoryImpl(services(enabled = false), api.instance, noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.friendsOnLesson(42, date))
        assertEquals(0, api.calls)
    }

    @Test
    fun `profiles become trimmed summaries with viewer scoped sharing in server order`() = runTest {
        val api = FakeLessonFriendsApi().apply {
            result = { pairId, requestedDate ->
                assertEquals(42L, pairId); assertEquals(date, requestedDate)
                ApiResponse.success(listOf(
                    profile(300003, " Второй ", UserCapabilities(true, false)),
                    profile(200002, "Первый", UserCapabilities(true, true))
                ))
            }
        }
        val repository = LessonFriendsRepositoryImpl(services(enabled = true), api.instance, noDemo(), dispatchers = dispatchers)

        val friends = (repository.friendsOnLesson(42, date) as AppResult.Success).value

        assertEquals(listOf(300003, 200002), friends.map { it.isu })
        assertEquals(
            UserSummary(300003, "Второй", null, listOf(UserGroup("M3100", 1, "ФТМИ")), UserSharing(sport = false, schedule = true)),
            friends.first()
        )
        assertEquals(1, api.calls)
    }

    @Test
    fun `transport failures and empty envelopes are errors, not empty lists`() = runTest {
        val failing = FakeLessonFriendsApi().apply { result = { _, _ -> throw IOException("offline") } }
        assertEquals(AppResult.Failure(AppError.Network), LessonFriendsRepositoryImpl(services(true), failing.instance, noDemo(), dispatchers).friendsOnLesson(42, date))

        val empty = FakeLessonFriendsApi().apply { result = { _, _ -> ApiResponse(success = true, data = null, error = null) } }
        val result = LessonFriendsRepositoryImpl(services(true), empty.instance, noDemo(), dispatchers).friendsOnLesson(42, date)
        assertEquals(true, result is AppResult.Failure)
    }

    private fun profile(isu: Int, name: String, capabilities: UserCapabilities) = UserProfile(
        UserData(isu, name, null, listOf(GroupData("M3100", 1, "ФТМИ")), capabilities),
        RelationshipState.FRIENDS
    )

    private fun services(enabled: Boolean) = FakeBackendGate(enabled)

    /** Only the lesson-friends call is answered; anything else is a test bug. */
    private class FakeLessonFriendsApi {
        var result: (Long, LocalDate) -> ApiResponse<List<UserProfile>> = { _, _ -> ApiResponse.success(emptyList()) }
        var calls = 0
            private set

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "friendsOnLesson" -> {
                    calls += 1
                    val continuation = arguments.last() as Continuation<Any?>
                    val value = result(arguments[0] as Long, (arguments[1] as java.time.LocalDate).toKotlinLocalDate())
                    continuation.resumeWith(Result.success(value))
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }
                else -> error("Unexpected call ${method.name}")
            }
        } as ItmoWidgetsApi
    }
}
