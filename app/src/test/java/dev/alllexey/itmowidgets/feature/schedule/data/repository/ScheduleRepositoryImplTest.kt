package dev.alllexey.itmowidgets.feature.schedule.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.schedule.data.local.CacheEntry
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryImplTest {

    private val local = FakeLocalDataSource()
    private val remote = FakeRemoteDataSource()

    @Test
    fun `refuses another user's schedule while custom services are off`() = runTest {
        val repository = createRepository(customServicesEnabled = false)

        val result = repository.refreshSchedule(
            userIsu = 123456,
            startDate = DATE,
            endDate = DATE
        )

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), result)
        assertTrue("backend must not be reached", remote.requestedUsers.isEmpty())
    }

    @Test
    fun `loads own schedule while custom services are off`() = runTest {
        val repository = createRepository(customServicesEnabled = false)

        val result = repository.refreshSchedule(
            userIsu = null,
            startDate = DATE,
            endDate = DATE
        )

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf<Int?>(null), remote.requestedUsers)
    }

    @Test
    fun `loads another user's schedule once custom services are on`() = runTest {
        val repository = createRepository(customServicesEnabled = true)

        val result = repository.refreshSchedule(
            userIsu = 123456,
            startDate = DATE,
            endDate = DATE
        )

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf<Int?>(123456), remote.requestedUsers)
    }

    private fun createRepository(customServicesEnabled: Boolean): ScheduleRepositoryImpl {
        return ScheduleRepositoryImpl(
            local = local,
            remote = remote,
            customServices = FakeCustomServices(customServicesEnabled)
        )
    }

    private class FakeRemoteDataSource : ScheduleRemoteDataSource {
        val requestedUsers = mutableListOf<Int?>()

        override suspend fun getSchedule(
            userIsu: Int?,
            start: LocalDate,
            end: LocalDate
        ): List<DaySchedule> {
            requestedUsers += userIsu
            return emptyList()
        }
    }

    private class FakeLocalDataSource : ScheduleLocalDataSource {
        override fun observeRange(
            userIsu: Int?,
            start: LocalDate,
            end: LocalDate
        ): Flow<List<DaySchedule>> = flowOf(emptyList())

        override suspend fun save(schedule: DaySchedule, userIsu: Int?) = Unit

        override suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry? = null

        override suspend fun clear() = Unit
    }

    private class FakeCustomServices(enabled: Boolean) : CustomServicesRepository {
        private val state = MutableStateFlow(enabled)

        override fun observeEnabled(): Flow<Boolean> = state

        override suspend fun isEnabled(): Boolean = state.value

        override suspend fun setEnabled(enabled: Boolean) {
            state.value = enabled
        }
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 2, 16)
    }
}
