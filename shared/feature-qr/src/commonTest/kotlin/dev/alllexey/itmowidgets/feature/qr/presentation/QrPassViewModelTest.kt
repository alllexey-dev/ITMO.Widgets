package dev.alllexey.itmowidgets.feature.qr.presentation

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.shared.feature.qr.QrStrings
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class QrPassViewModelTest {
    private val main = TestMainDispatcher()
    private val zone = TimeZone.of("Europe/Moscow")
    private val savedAt = LocalDateTime(LocalDate(2026, 10, 7), LocalTime(11, 44)).toInstant(zone)
    private val clock = FakeClock(savedAt)
    private val sessions = FakeSessionRepository(SessionState.SignedIn(CurrentUser(123456, "Иванова Мария", null)))
    private val social = FakeSocialRepository()
    private val repository = FakeQrCodeRepository(QrCodeSnapshot("ITMO-TEST", savedAt.toEpochMilliseconds()))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun viewModel() = QrPassViewModel(repository, FakeAppearance(), sessions, social, clock, zone)

    @Test
    fun aFreshCodeShowsAtOnceWithItsTimeAndAsksNobody() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.start()
        runCurrent()

        assertEquals(QrPassCode("ITMO-TEST", useDynamicColors = false), vm.uiState.value.code)
        assertEquals(QrPassStatus.Ready("11:44"), vm.uiState.value.status)
        assertEquals(UiText.Res(QrStrings.qr_pass_updated_at, listOf("11:44")), vm.uiState.value.status.line)
        assertEquals(emptyList(), repository.calls)
        vm.stop()
    }

    @Test
    fun anOldCodeStaysDimmedWhileANewOneLoads() = runTest(main.dispatcher) {
        clock.advanceBy(2.hours)
        repository.pending = CompletableDeferred()
        val vm = viewModel()
        vm.start()
        runCurrent()

        assertEquals(QrPassStatus.Refreshing, vm.uiState.value.status)
        assertEquals("ITMO-TEST", vm.uiState.value.code?.hex)
        repository.last = QrCodeSnapshot("ITMO-NEW", clock.now().toEpochMilliseconds())
        repository.pending!!.complete(AppResult.Success(Unit))
        runCurrent()

        assertEquals(QrPassStatus.Ready("13:44"), vm.uiState.value.status)
        assertEquals("ITMO-NEW", vm.uiState.value.code?.hex)
        assertEquals(listOf(false), repository.calls)
        vm.stop()
    }

    @Test
    fun withoutNetworkTheLastCodeStaysWithItsDate() = runTest(main.dispatcher) {
        clock.advanceBy(24.hours)
        repository.result = AppResult.Failure(AppError.Network)
        val vm = viewModel()
        vm.start()
        runCurrent()

        assertEquals("ITMO-TEST", vm.uiState.value.code?.hex)
        assertEquals(QrPassStatus.Offline("7 окт., 11:44"), vm.uiState.value.status)
        vm.stop()
    }

    @Test
    fun forceAlwaysAsksAndAnotherFailureKeepsTheCodeAndReportsIt() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.start()
        runCurrent()
        repository.result = AppResult.Failure(AppError.Unauthorized)
        val event = async { vm.events.first() }

        vm.refresh(RefreshMode.Force)
        vm.refresh(RefreshMode.Force)
        runCurrent()

        assertEquals(listOf(true), repository.calls)
        assertEquals(QrPassStatus.Ready("11:44"), vm.uiState.value.status)
        assertEquals(QrPassEvent.RefreshFailed(AppError.Unauthorized), event.await())
        vm.stop()
    }

    @Test
    fun withoutAnyCodeTheSheetSaysEmptyOrTheError() = runTest(main.dispatcher) {
        repository.last = null
        val vm = viewModel()
        vm.start()
        runCurrent()
        assertEquals(QrPassStatus.Empty, vm.uiState.value.status)
        assertEquals(null, vm.uiState.value.code)

        repository.result = AppResult.Failure(AppError.Network)
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(QrPassStatus.Failed(AppError.Network), vm.uiState.value.status)
        vm.stop()
    }

    @Test
    fun aStoppedSheetRequestsNothing() = runTest(main.dispatcher) {
        clock.advanceBy(2.hours)
        val vm = viewModel()
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(emptyList(), repository.calls)
        assertEquals(QrPassStatus.Loading, vm.uiState.value.status)
    }

    @Test
    fun theHolderJoinsTheSessionAndTheBackendGroup() = runTest(main.dispatcher) {
        val vm = viewModel()
        runCurrent()
        assertEquals(
            QrPassHolder("Иванова Мария", UiText.Res(QrStrings.qr_pass_isu, listOf(123456))),
            vm.uiState.value.holder,
        )

        social.currentUser.value = UserSummary(
            isu = 123456,
            name = "Иванова Мария",
            pictureUrl = null,
            groups = listOf(UserGroup("M3105", 1, "ФИТиП"), UserGroup("M3205", 2, "ФИТиП")),
            sharing = UserSharing(sport = false, schedule = false),
        )
        runCurrent()
        assertEquals(
            QrPassHolder("Иванова Мария", UiText.Res(QrStrings.qr_pass_group_isu, listOf("M3205", 123456))),
            vm.uiState.value.holder,
        )

        sessions.mutableState.value = SessionState.SignedOut
        social.currentUser.value = null
        runCurrent()
        assertEquals(null, vm.uiState.value.holder)
    }

    private class FakeQrCodeRepository(var last: QrCodeSnapshot?) : QrCodeRepository {
        var result: AppResult<Unit> = AppResult.Success(Unit)
        var pending: CompletableDeferred<AppResult<Unit>>? = null
        val calls = mutableListOf<Boolean>()
        override suspend fun currentQr() = last
        override suspend fun lastQr() = last
        override suspend fun currentQrHex(allowExpired: Boolean) = last?.hex
        override fun observeQrHex() = flowOf(last?.hex.orEmpty())
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> {
            calls += force
            return pending?.await() ?: result
        }
        override fun clearCache() {
            last = null
        }
    }

    private class FakeAppearance : QrAppearancePreferences {
        override suspend fun useDynamicColors() = false
        override suspend fun isSpoilerEnabled() = true
        override suspend fun spoilerAnimationType() = QrAnimationType.entries.first()
    }
}
