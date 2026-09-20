package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
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
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonFriendsRepositoryImplTest {
    private val date = LocalDate.of(2026, 9, 8)

    @Test
    fun `without the opt-in nothing is requested`() = runTest {
        val api = FakeApi()
        val repository = LessonFriendsRepositoryImpl(services(enabled = false), api.instance)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.friendsOnLesson(42, date))
        assertEquals(0, api.calls)
    }

    @Test
    fun `profiles become trimmed summaries with viewer scoped sharing in server order`() = runTest {
        val api = FakeApi().apply {
            result = { pairId, requestedDate ->
                assertEquals(42L, pairId); assertEquals(date, requestedDate)
                ApiResponse.success(listOf(
                    profile(300003, " Второй ", UserCapabilities(true, false)),
                    profile(200002, "Первый", UserCapabilities(true, true))
                ))
            }
        }
        val repository = LessonFriendsRepositoryImpl(services(enabled = true), api.instance)

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
        val failing = FakeApi().apply { result = { _, _ -> throw IOException("offline") } }
        assertEquals(AppResult.Failure(AppError.Network), LessonFriendsRepositoryImpl(services(true), failing.instance).friendsOnLesson(42, date))

        val empty = FakeApi().apply { result = { _, _ -> ApiResponse(success = true, data = null, error = null) } }
        val result = LessonFriendsRepositoryImpl(services(true), empty.instance).friendsOnLesson(42, date)
        assertEquals(true, result is AppResult.Failure)
    }

    private fun profile(isu: Int, name: String, capabilities: UserCapabilities) = UserProfile(
        UserData(isu, name, null, listOf(GroupData("M3100", 1, "ФТМИ")), capabilities),
        RelationshipState.FRIENDS
    )

    private fun services(enabled: Boolean) = object : CustomServicesRepository {
        override fun observeEnabled(): Flow<Boolean> = flowOf(enabled)
        override suspend fun isEnabled(): Boolean = enabled
        override suspend fun setEnabled(enabled: Boolean) = Unit
    }

    /** Only the lesson-friends call is answered; anything else is a test bug. */
    private class FakeApi {
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
                    val value = result(arguments[0] as Long, arguments[1] as LocalDate)
                    continuation.resumeWith(Result.success(value))
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }
                else -> error("Unexpected call ${method.name}")
            }
        } as ItmoWidgetsApi
    }
}
