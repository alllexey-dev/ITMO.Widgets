package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.IOException

class QrCodeRepositoryImplTest {

    @Test
    fun cachedValueSkipsRemoteRequest() = runTest {
        val local = FakeQrCodeLocalDataSource(cached = "cached")
        val remote = FakeQrCodeRemoteDataSource()
        val repository = QrCodeRepositoryImpl(local, remote, dispatchers())

        val result = repository.refreshQrHex(force = false)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(QrCodeSnapshot("cached", 3_600_000L), repository.currentQr())
        assertEquals(0, remote.requestCount)
        assertEquals(emptyList(), local.savedValues)
    }

    @Test
    fun forcedRefreshReplacesCachedValue() = runTest {
        val local = FakeQrCodeLocalDataSource(cached = "cached")
        val remote = FakeQrCodeRemoteDataSource(value = "fresh")
        val repository = QrCodeRepositoryImpl(local, remote, dispatchers())

        val result = repository.refreshQrHex(force = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(1, remote.requestCount)
        assertEquals(listOf("fresh"), local.savedValues)
    }

    @Test
    fun remoteFailureIsMappedAtDataBoundary() = runTest {
        val result = refreshFailingWith(IllegalStateException("boom"))

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun aFailureBeforeAnyAnswerIsANetworkError() = runTest {
        val result = refreshFailingWith(IllegalStateException("refresh", IOException("no route")))

        assertEquals(AppResult.Failure(AppError.Network), result)
    }

    @Test
    fun aWrappedClientFailureKeepsItsMeaning() = runTest {
        val result = refreshFailingWith(IllegalStateException("wrapped", MyItmoException.Auth(401)))

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
    }

    @Test
    fun expiredCacheRemainsAvailableForWidgetFallback() = runTest {
        val local = FakeQrCodeLocalDataSource(cached = "last-working-code", expired = true)
        val repository = QrCodeRepositoryImpl(local, FakeQrCodeRemoteDataSource(), dispatchers())

        assertEquals(null, repository.currentQr())
        assertEquals(null, repository.currentQrHex())
        assertEquals("last-working-code", repository.currentQrHex(allowExpired = true))
    }

    @Test
    fun clearingTheSessionForgetsThePass() = runTest {
        val local = FakeQrCodeLocalDataSource(cached = "cached")
        val repository = QrCodeRepositoryImpl(local, FakeQrCodeRemoteDataSource(), dispatchers())

        repository.clearSessionData()

        assertEquals(null, repository.currentQrHex(allowExpired = true))
    }

    private suspend fun TestScope.refreshFailingWith(failure: Exception): AppResult<Unit> =
        QrCodeRepositoryImpl(
            local = FakeQrCodeLocalDataSource(),
            remote = FakeQrCodeRemoteDataSource(failure = failure),
            dispatchers = dispatchers()
        ).refreshQrHex()

    private fun TestScope.dispatchers(): AppDispatchers =
        StandardTestDispatcher(testScheduler).let { AppDispatchers(io = it, default = it, main = it) }

    private class FakeQrCodeLocalDataSource(
        private var cached: String? = null,
        private var expired: Boolean = false
    ) : QrCodeLocalDataSource {

        val savedValues = mutableListOf<String>()

        override fun snapshot() = get(false)?.let { QrCodeSnapshot(it, 3_600_000L) }

        override fun observe(): Flow<String> = flowOf(cached.orEmpty())

        override fun get(allowExpired: Boolean): String? {
            return cached.takeIf { allowExpired || !expired }
        }

        override fun save(hex: String) {
            cached = hex
            expired = false
            savedValues += hex
        }

        override fun clear() {
            cached = null
        }
    }

    private class FakeQrCodeRemoteDataSource(
        private val value: String = "remote",
        private val failure: Exception? = null
    ) : QrCodeRemoteDataSource {

        var requestCount = 0

        override suspend fun getQrHex(): String {
            requestCount += 1
            failure?.let { throw it }
            return value
        }
    }
}
