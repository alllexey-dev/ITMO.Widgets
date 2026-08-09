package dev.alllexey.itmowidgets.feature.qr.data.repository

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeRepositoryImplTest {

    @Test
    fun `cached value skips remote request`() = runTest {
        val local = FakeLocalDataSource(cached = "cached")
        val remote = FakeRemoteDataSource()
        val repository = QrCodeRepositoryImpl(local, remote)

        val result = repository.refreshQrHex(force = false)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(0, remote.requestCount)
        assertEquals(emptyList<String>(), local.savedValues)
    }

    @Test
    fun `forced refresh replaces cached value`() = runTest {
        val local = FakeLocalDataSource(cached = "cached")
        val remote = FakeRemoteDataSource(value = "fresh")
        val repository = QrCodeRepositoryImpl(local, remote)

        val result = repository.refreshQrHex(force = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(1, remote.requestCount)
        assertEquals(listOf("fresh"), local.savedValues)
    }

    @Test
    fun `remote failure is mapped at data boundary`() = runTest {
        val repository = QrCodeRepositoryImpl(
            local = FakeLocalDataSource(),
            remote = FakeRemoteDataSource(failure = IllegalStateException("boom"))
        )

        val result = repository.refreshQrHex()

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Unknown)
    }

    @Test
    fun `expired cache remains available for widget fallback`() = runTest {
        val local = FakeLocalDataSource(cached = "last-working-code", expired = true)
        val repository = QrCodeRepositoryImpl(local, FakeRemoteDataSource())

        assertEquals(null, repository.currentQrHex())
        assertEquals(
            "last-working-code",
            repository.currentQrHex(allowExpired = true)
        )
    }

    private class FakeLocalDataSource(
        private var cached: String? = null,
        private var expired: Boolean = false
    ) : QrCodeLocalDataSource {

        val savedValues = mutableListOf<String>()

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

    private class FakeRemoteDataSource(
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
