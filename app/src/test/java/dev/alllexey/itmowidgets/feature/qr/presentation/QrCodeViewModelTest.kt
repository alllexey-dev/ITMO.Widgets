package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QrCodeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `first load uses cache and repeated refresh cannot duplicate the request`() = runTest {
        val repository = FakeRepository()
        val vm = QrCodeViewModel(repository, clock())
        vm.start()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        assertEquals(listOf(false), repository.calls)
        repository.pending = CompletableDeferred()
        vm.refresh()
        vm.refresh()
        runCurrent()
        assertEquals(listOf(false, true), repository.calls)
        assertTrue((vm.uiState.value as QrCodeUiState.Content).refreshing)
        vm.stop()
    }

    @Test fun `a fresh screen shows the cached pass before the network answers`() = runTest {
        val repository = FakeRepository()
        repository.pending = CompletableDeferred()
        val vm = QrCodeViewModel(repository, clock())
        vm.start()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!, refreshing = true), vm.uiState.value)
        repository.pending!!.complete(AppResult.Success(Unit))
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        vm.stop()
    }

    @Test fun `expiry hides the pass even during a pending refresh and failed refresh never revives it`() = runTest {
        val repository = FakeRepository()
        val vm = QrCodeViewModel(repository, clock())
        vm.start()
        runCurrent()
        repository.pending = CompletableDeferred()
        vm.refresh()
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(QrCodeUiState.Loading, vm.uiState.value)
        repository.pending!!.complete(AppResult.Failure(AppError.Network))
        runCurrent()
        assertEquals(QrCodeUiState.Error(AppError.Network), vm.uiState.value)
        vm.stop()
    }

    @Test fun `refresh failure keeps only a still valid pass and offers retry feedback`() = runTest {
        val repository = FakeRepository()
        val vm = QrCodeViewModel(repository, clock())
        vm.start()
        runCurrent()
        repository.result = AppResult.Failure(AppError.Network)
        val error = async { vm.refreshErrors.first() }
        vm.refresh()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        assertEquals(AppError.Network, error.await())
        vm.stop()
    }

    @Test fun `returning after expiry clears old content before loading and empty differs from error`() = runTest {
        val repository = FakeRepository()
        val vm = QrCodeViewModel(repository, clock())
        vm.start()
        runCurrent()
        vm.stop()
        advanceTimeBy(1000)
        assertEquals(1, repository.calls.size)
        vm.start()
        assertEquals(QrCodeUiState.Loading, vm.uiState.value)
        runCurrent()
        assertEquals(QrCodeUiState.Empty, vm.uiState.value)
        repository.result = AppResult.Failure(AppError.Unauthorized)
        vm.refresh()
        runCurrent()
        assertEquals(QrCodeUiState.Error(AppError.Unauthorized), vm.uiState.value)
        repository.result = AppResult.Success(Unit)
        repository.code = QrCodeSnapshot("NEW-TEST", 2000)
        vm.refresh()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        vm.stop()
    }

    private fun TestScope.clock() = object : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
    }

    private class FakeRepository : QrCodeRepository {
        var code: QrCodeSnapshot? = QrCodeSnapshot("ITMO-TEST", 1000)
        var result: AppResult<Unit> = AppResult.Success(Unit)
        var pending: CompletableDeferred<AppResult<Unit>>? = null
        val calls = mutableListOf<Boolean>()
        override suspend fun currentQr() = code
        override suspend fun currentQrHex(allowExpired: Boolean) = code?.hex
        override fun observeQrHex() = flowOf(code?.hex.orEmpty())
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> {
            calls += force
            return pending?.await() ?: result
        }
        override fun clearCache() { code = null }
    }
}
