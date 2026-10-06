package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.testing.unreachable
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UserSportRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test
    fun `without the opt-in a friend's sport is not requested`() = runTest {
        val users = UserSportRepositoryImpl(FakeBackendGate(optedIn = false), unreachable<SportApi>(), FixedAcademicTime(), noDemo(), dispatchers = dispatchers)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), users.getUserBookings(123456))
    }

    @Test
    fun `a sport the owner does not share is the lock state`() = runTest {
        val clients = Core2Harness(Core2Harness.session()) {
            respondJson(Core2Harness.errorEnvelope("access_denied"), HttpStatusCode.Forbidden)
        }
        val users = UserSportRepositoryImpl(FakeBackendGate(optedIn = true), clients.client.sport, FixedAcademicTime(), noDemo(), dispatchers)

        assertEquals(AppResult.Failure(AppError.Forbidden), users.getUserBookings(123456))
        assertEquals("/api/sport/users/123456/bookings", clients.backendRequests.single().url.encodedPath)
    }
}
