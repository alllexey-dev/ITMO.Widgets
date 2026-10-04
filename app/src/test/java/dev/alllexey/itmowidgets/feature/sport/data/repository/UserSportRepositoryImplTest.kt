package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.testing.unreachable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserSportRepositoryImplTest {

    @Test
    fun `without the opt-in a friend's sport is not requested`() = runTest {
        val users = UserSportRepositoryImpl(FakeBackendGate(optedIn = false), unreachable<ItmoWidgetsApi>(), FixedAcademicTime(), noDemo())

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), users.getUserBookings(123456))
    }
}
