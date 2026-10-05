package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class QrCodeViewModelTest {
    private val main = TestMainDispatcher()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(0))
    private val appearance = FakeQrAppearancePreferences()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun firstLoadUsesCacheAndRepeatedRefreshCannotDuplicateTheRequest() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        assertEquals(listOf(false), repository.calls)
        repository.pending = CompletableDeferred()
        vm.refresh(RefreshMode.Force)
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(listOf(false, true), repository.calls)
        assertTrue((vm.uiState.value as QrCodeUiState.Content).refreshing)
        vm.stop()
    }

    @Test
    fun onlyForceBypassesTheCache() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        vm.refresh(RefreshMode.Silent)
        runCurrent()
        vm.refresh(RefreshMode.Pull)
        runCurrent()
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(listOf(false, false, false, true), repository.calls)
        vm.stop()
    }

    @Test
    fun aFreshScreenShowsTheCachedPassBeforeTheNetworkAnswers() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        repository.pending = CompletableDeferred()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!, refreshing = true), vm.uiState.value)
        repository.pending!!.complete(AppResult.Success(Unit))
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        vm.stop()
    }

    @Test
    fun expiryHidesThePassEvenDuringAPendingRefreshAndAFailedRefreshNeverRevivesIt() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        repository.pending = CompletableDeferred()
        vm.refresh(RefreshMode.Force)
        runCurrent()
        advance(1000)
        assertEquals(QrCodeUiState.Loading, vm.uiState.value)
        repository.pending!!.complete(AppResult.Failure(AppError.Network))
        runCurrent()
        assertEquals(QrCodeUiState.Error(AppError.Network), vm.uiState.value)
        vm.stop()
    }

    @Test
    fun refreshFailureKeepsOnlyAStillValidPassAndOffersRetryFeedback() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        repository.result = AppResult.Failure(AppError.Network)
        val event = async { vm.events.first() }
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        assertEquals(QrCodeEvent.RefreshFailed(AppError.Network), event.await())
        vm.stop()
    }

    @Test
    fun aFailureReportedWhileNobodyCollectsReachesTheNextCollector() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        repository.result = AppResult.Failure(AppError.Network)
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(QrCodeEvent.RefreshFailed(AppError.Network), vm.events.first())
        vm.stop()
    }

    @Test
    fun returningAfterExpiryClearsOldContentBeforeLoadingAndEmptyDiffersFromError() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        vm.stop()
        advance(1000)
        assertEquals(1, repository.calls.size)
        vm.start()
        assertEquals(QrCodeUiState.Loading, vm.uiState.value)
        runCurrent()
        assertEquals(QrCodeUiState.Empty, vm.uiState.value)
        repository.result = AppResult.Failure(AppError.Unauthorized)
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(QrCodeUiState.Error(AppError.Unauthorized), vm.uiState.value)
        repository.result = AppResult.Success(Unit)
        repository.code = QrCodeSnapshot("NEW-TEST", 2000)
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!), vm.uiState.value)
        vm.stop()
    }

    @Test
    fun contentCarriesTheColourSettingAndAReturnPicksUpItsChange() = runTest(main.dispatcher) {
        val repository = FakeQrCodeRepository()
        repository.code = QrCodeSnapshot("ITMO-TEST", 60_000)
        appearance.dynamicColors = true
        val vm = QrCodeViewModel(repository, appearance, clock)
        vm.start()
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!, useDynamicColors = true), vm.uiState.value)
        vm.stop()

        appearance.dynamicColors = false
        repository.pending = CompletableDeferred()
        vm.start()
        runCurrent()
        // The still valid pass switches colours at once, before the network answers.
        assertEquals(
            QrCodeUiState.Content(repository.code!!, refreshing = true, useDynamicColors = false),
            vm.uiState.value,
        )
        repository.pending!!.complete(AppResult.Success(Unit))
        runCurrent()
        assertEquals(QrCodeUiState.Content(repository.code!!, useDynamicColors = false), vm.uiState.value)
        vm.stop()
    }

    /** Moves the wall clock and the test scheduler together, as real time moves both. */
    private fun TestScope.advance(millis: Long) {
        clock.advanceBy(millis.milliseconds)
        advanceTimeBy(millis)
        runCurrent()
    }

    private class FakeQrCodeRepository : QrCodeRepository {
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

    private class FakeQrAppearancePreferences : QrAppearancePreferences {
        var dynamicColors = false
        override suspend fun useDynamicColors() = dynamicColors
        override suspend fun isSpoilerEnabled() = true
        override suspend fun spoilerAnimationType() = QrAnimationType.entries.first()
    }
}
