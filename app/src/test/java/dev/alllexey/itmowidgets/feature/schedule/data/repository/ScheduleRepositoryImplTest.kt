package dev.alllexey.itmowidgets.feature.schedule.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.schedule.data.local.CacheEntry
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        assertTrue(local.replacements.isEmpty())
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

    @Test
    fun `successful refresh replaces only its user and range in one batch`() = runTest {
        val end = DATE.plusDays(2)
        remote.schedules = listOf(day(DATE), day(end))

        val result = createRepository(true).refreshSchedule(123456, DATE, end)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(Replacement(123456, DATE, end, remote.schedules)), local.replacements)
        assertEquals(0, local.saves)
        assertEquals(0, local.clears)
    }

    @Test
    fun `empty successful response still replaces the requested range`() = runTest {
        assertEquals(AppResult.Success(Unit), createRepository(false).refreshSchedule(null, DATE, DATE))
        assertEquals(listOf(Replacement(null, DATE, DATE, emptyList())), local.replacements)
        assertEquals(0, local.saves)
        assertEquals(0, local.clears)
    }

    @Test
    fun `network failure never mutates the local cache`() = runTest {
        remote.error = IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), createRepository(true).refreshSchedule(null, DATE, DATE))
        assertTrue(local.replacements.isEmpty())
        assertEquals(0, local.saves)
        assertEquals(0, local.clears)
    }

    @Test
    fun `cancelled request propagates cancellation without cache changes`() = runTest {
        remote.error = CancellationException("cancelled")
        try {
            createRepository(true).refreshSchedule(null, DATE, DATE)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(local.replacements.isEmpty())
            assertEquals(0, local.saves)
            assertEquals(0, local.clears)
        }
    }

    private fun day(date: LocalDate) = DaySchedule(date.dayOfWeek.value, 1, date, null, emptyList())

    private data class Replacement(val userIsu: Int?, val start: LocalDate, val end: LocalDate, val schedules: List<DaySchedule>)

    private fun createRepository(customServicesEnabled: Boolean): ScheduleRepositoryImpl {
        return ScheduleRepositoryImpl(
            local = local,
            remote = remote,
            customServices = FakeCustomServices(customServicesEnabled)
        )
    }

    private class FakeRemoteDataSource : ScheduleRemoteDataSource {
        val requestedUsers = mutableListOf<Int?>()
        var schedules: List<DaySchedule> = emptyList()
        var error: Exception? = null

        override suspend fun getSchedule(
            userIsu: Int?,
            start: LocalDate,
            end: LocalDate
        ): List<DaySchedule> {
            requestedUsers += userIsu
            error?.let { throw it }
            return schedules
        }
    }

    private class FakeLocalDataSource : ScheduleLocalDataSource {
        val replacements = mutableListOf<Replacement>()
        var saves = 0
        var clears = 0
        override fun observeRange(
            userIsu: Int?,
            start: LocalDate,
            end: LocalDate
        ): Flow<List<DaySchedule>> = flowOf(emptyList())

        override suspend fun save(schedule: DaySchedule, userIsu: Int?) { saves++ }

        override suspend fun replaceRange(userIsu: Int?, start: LocalDate, end: LocalDate, schedules: List<DaySchedule>) {
            replacements += Replacement(userIsu, start, end, schedules)
        }

        override suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry? = null

        override suspend fun clear() { clears++ }
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
