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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `access denial discards all dates of only the foreign user`() = runTest {
        val repository = createRepository(true)
        for ((status, expected) in listOf(401 to AppError.Unauthorized, 403 to AppError.Forbidden, 404 to AppError.NotFound)) {
            remote.error = HttpException(Response.error<Unit>(status, "".toResponseBody()))
            assertEquals(AppResult.Failure(expected), repository.refreshSchedule(123456, DATE, DATE))
        }
        assertEquals(listOf(123456, 123456, 123456), local.clearedUsers)
        assertEquals(0, local.clears)
        assertTrue(local.replacements.isEmpty())
    }

    @Test
    fun `own authorization failure and foreign network failure preserve caches`() = runTest {
        val repository = createRepository(true)
        remote.error = HttpException(Response.error<Unit>(403, "".toResponseBody()))
        repository.refreshSchedule(null, DATE, DATE)
        remote.error = IOException("offline")
        repository.refreshSchedule(123456, DATE, DATE)
        assertTrue(local.clearedUsers.isEmpty())
        assertEquals(0, local.clears)
    }

    @Test
    fun `late successful response cannot restore a concurrently revoked user`() = runTest {
        val repository = createRepository(true)
        val oldResponse = CompletableDeferred<List<DaySchedule>>()
        remote.response = { start ->
            if (start == DATE) oldResponse.await()
            else throw HttpException(Response.error<Unit>(403, "".toResponseBody()))
        }
        val old = async { repository.refreshSchedule(123456, DATE, DATE) }
        runCurrent()
        assertEquals(AppResult.Failure(AppError.Forbidden), repository.refreshSchedule(123456, DATE.plusDays(1), DATE.plusDays(1)))
        oldResponse.complete(listOf(day(DATE)))
        assertEquals(AppResult.Failure(AppError.Forbidden), old.await())
        assertTrue(local.replacements.isEmpty())
        assertEquals(listOf(123456), local.clearedUsers)

        // A new server-authorized request after revocation is allowed to reload.
        remote.response = { listOf(day(DATE)) }
        assertEquals(AppResult.Success(Unit), repository.refreshSchedule(123456, DATE, DATE))
        assertEquals(1, local.replacements.size)
    }

    @Test
    fun `cancelling after a known denial cannot interrupt private cache removal`() = runTest {
        val gate = CompletableDeferred<Unit>()
        local.clearGate = gate
        remote.error = HttpException(Response.error<Unit>(403, "".toResponseBody()))
        val request = async { createRepository(true).refreshSchedule(123456, DATE, DATE) }
        local.clearStarted.await()
        request.cancel()
        runCurrent()
        assertTrue(local.clearedUsers.isEmpty())
        gate.complete(Unit)
        request.join()
        assertEquals(listOf(123456), local.clearedUsers)
    }

    @Test
    fun `session cleanup rejects late successes for both own and foreign data`() = runTest {
        val repository = createRepository(true)
        for (user in listOf(null, 123456)) {
            val response = CompletableDeferred<List<DaySchedule>>()
            remote.response = { response.await() }
            val old = async { repository.refreshSchedule(user, DATE, DATE) }
            runCurrent()
            repository.clearSessionData()
            response.complete(listOf(day(DATE)))
            assertTrue(old.await() is AppResult.Failure)
            assertTrue(local.replacements.isEmpty())
        }
        assertEquals(2, local.clears)
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
        var response: (suspend (LocalDate) -> List<DaySchedule>)? = null

        override suspend fun getSchedule(
            userIsu: Int?,
            start: LocalDate,
            end: LocalDate
        ): List<DaySchedule> {
            requestedUsers += userIsu
            response?.let { return it(start) }
            error?.let { throw it }
            return schedules
        }
    }

    private class FakeLocalDataSource : ScheduleLocalDataSource {
        val replacements = mutableListOf<Replacement>()
        var saves = 0
        var clears = 0
        val clearedUsers = mutableListOf<Int>()
        var clearGate: CompletableDeferred<Unit>? = null
        val clearStarted = CompletableDeferred<Unit>()
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
        override suspend fun clearUser(userIsu: Int) {
            clearStarted.complete(Unit)
            clearGate?.await()
            clearedUsers += userIsu
        }
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
