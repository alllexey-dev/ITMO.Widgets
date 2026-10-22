package dev.alllexey.itmowidgets.feature.connections.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.connections.domain.ClientItmoSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.HpkeSender
import dev.alllexey.itmowidgets.feature.connections.domain.PairingError
import dev.alllexey.itmowidgets.feature.connections.domain.PairingKind
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import dev.alllexey.itmowidgets.feature.connections.domain.PairingSealer
import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class PairingViewModelTest {

    private val main = TestMainDispatcher()
    private val session = ClientItmoSession(
        isu = 100001,
        sid = "sid-new",
        expiresAt = Instant.parse("2027-01-20T09:00:00Z"),
        refreshToken = "test-refresh-token",
        refreshExpiresAt = Instant.parse("2026-11-06T09:00:00Z"),
    )
    private val pairing = FakePairingRepository(PairingResult.Success(Samples.request()))
    private val signIn = FakeClientSignIn(ClientSignInResult.Success(session))
    private val devices = FakeDevicesRepository()
    private val logins = FakeItmoLogins()
    private val sealed = mutableListOf<ByteArray>()
    private val sealer = PairingSealer(HpkeSender { _, _, plaintext -> sealed += plaintext; ByteArray(64) })

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun viewModel() =
        PairingViewModel(SavedStateHandle(), pairing, signIn, sealer, devices, logins, FixedAcademicTime())

    @Test
    fun textThatIsNoPairingLinkStaysWithAHint() = runTest(main.dispatcher) {
        val model = viewModel()

        model.onTextChanged("https://widgets.alllexey.dev/app/login?code=ABCD2345")
        model.submit()

        val input = assertIs<PairingUiState.Input>(model.uiState.value)
        assertNotNull(input.error)
    }

    @Test
    fun anAgentIsConfirmedWithTheCodeAndItsRequestedScopes() = runTest(main.dispatcher) {
        val model = viewModel()

        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()

        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertEquals("765 323", confirm.code)
        assertEquals(listOf("SPORT", "FRIENDS_READ"), confirm.scopes.map { it.id })
        assertTrue(confirm.scopes.all { it.checked })
        assertFalse(confirm.renewal)
    }

    @Test
    fun theUnlockedSignInIsSealedAndApprovedWithItsSidAndTheChosenScopes() = runTest(main.dispatcher) {
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        model.onScopeChanged("FRIENDS_READ", false)

        model.proceed(UNLOCKED)
        advanceUntilIdle()
        val page = assertIs<PairingUiState.SigningIn>(model.uiState.value)
        assertEquals("UA nas", page.userAgent)
        assertNull(page.code)
        assertFalse(model.onSignInNavigation("https://id.itmo.ru/auth/login"))
        assertTrue(model.onSignInNavigation(FakeClientSignIn.CALLBACK + "?code=c&state=s"))
        advanceUntilIdle()

        val result = assertIs<PairingUiState.Result>(model.uiState.value)
        assertTrue(result.success)
        val approval = pairing.approvals.single()
        assertEquals(listOf("SPORT"), approval.scopes)
        assertEquals("sid-new", approval.session?.sid)
        assertNotNull(approval.sealedBundle)
        assertTrue(""""sid":"sid-new"""" in sealed.single().decodeToString())
        assertTrue(logins.ended.isEmpty())
    }

    @Test
    fun anotherAccountAsksToSignInAgain() = runTest(main.dispatcher) {
        signIn.result = ClientSignInResult.OtherAccount
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        model.proceed(UNLOCKED)
        advanceUntilIdle()
        model.onSignInNavigation(FakeClientSignIn.CALLBACK)
        advanceUntilIdle()

        val result = assertIs<PairingUiState.Result>(model.uiState.value)
        assertEquals(ResultAction.SIGN_IN_AGAIN, result.next)
        assertTrue(pairing.approvals.isEmpty())
        model.onResultAction()
        advanceUntilIdle()
        assertIs<PairingUiState.SigningIn>(model.uiState.value)
        assertEquals(2, signIn.begun)
    }

    @Test
    fun theWebIsApprovedWithoutASignIn() = runTest(main.dispatcher) {
        pairing.opened = PairingResult.Success(Samples.request(client = ClientType.WEB))
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        assertTrue(assertIs<PairingUiState.Confirm>(model.uiState.value).scopes.isEmpty())

        model.proceed(UNLOCKED)
        advanceUntilIdle()

        assertTrue(assertIs<PairingUiState.Result>(model.uiState.value).success)
        val approval = pairing.approvals.single()
        assertNull(approval.sealedBundle)
        assertNull(approval.scopes)
        assertNull(approval.session)
        assertEquals(0, signIn.begun)
    }

    @Test
    fun aRenewalGoesStraightToTheLockAndEndsTheOldSession() = runTest(main.dispatcher) {
        pairing.opened = PairingResult.Success(Samples.request(kind = PairingKind.RENEW, connectionId = Samples.agentId))
        devices.state.value = Devices(listOf(Samples.agent(sid = "sid-old")), emptyList())
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        assertTrue(assertIs<PairingUiState.Confirm>(model.uiState.value).renewal)

        model.proceed(UNLOCKED)
        advanceUntilIdle()
        assertEquals("765 323", assertIs<PairingUiState.SigningIn>(model.uiState.value).code)
        model.onSignInNavigation(FakeClientSignIn.CALLBACK)
        advanceUntilIdle()

        assertTrue(assertIs<PairingUiState.Result>(model.uiState.value).success)
        assertNull(pairing.approvals.single().scopes)
        assertEquals(listOf(listOf("sid-old")), logins.ended)
    }

    @Test
    fun aFailedApprovalEndsTheNewSession() = runTest(main.dispatcher) {
        pairing.approved = PairingResult.Failure(PairingError.Failed(AppError.Network))
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        model.proceed(UNLOCKED)
        advanceUntilIdle()
        model.onSignInNavigation(FakeClientSignIn.CALLBACK)
        advanceUntilIdle()

        assertFalse(assertIs<PairingUiState.Result>(model.uiState.value).success)
        assertEquals(listOf(listOf("sid-new")), logins.ended)
    }

    @Test
    fun aCancelledLockKeepsTheCardAndAMissingOneExplains() = runTest(main.dispatcher) {
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()

        model.proceed { DeviceUnlockResult.CANCELLED }
        advanceUntilIdle()
        assertFalse(assertIs<PairingUiState.Confirm>(model.uiState.value).busy)

        model.proceed { DeviceUnlockResult.NOT_SET_UP }
        advanceUntilIdle()
        assertEquals(ResultAction.CLOSE, assertIs<PairingUiState.Result>(model.uiState.value).next)
        assertEquals(0, signIn.begun)
    }

    @Test
    fun aLinkOfAnotherQrAndAnExpiredCodeOfferANewScan() = runTest(main.dispatcher) {
        for (error in listOf(PairingError.LinkMismatch, PairingError.Failed(AppError.NotFound))) {
            pairing.opened = PairingResult.Failure(error)
            val model = viewModel()
            model.onScanned(PairingVectors.LINK)
            advanceUntilIdle()

            val result = assertIs<PairingUiState.Result>(model.uiState.value)
            assertEquals(ResultAction.SCAN_AGAIN, result.next, error.toString())
            model.onResultAction()
            assertIs<PairingUiState.Input>(model.uiState.value)
        }
    }

    @Test
    fun aClosedSignInPageSendsNothing() = runTest(main.dispatcher) {
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        model.proceed(UNLOCKED)
        advanceUntilIdle()

        model.onSignInCancelled()

        assertEquals(ResultAction.SIGN_IN_AGAIN, assertIs<PairingUiState.Result>(model.uiState.value).next)
        assertFalse(model.onSignInNavigation(FakeClientSignIn.CALLBACK))
        assertTrue(pairing.approvals.isEmpty())
    }

    private companion object {
        val UNLOCKED = DeviceUnlock { DeviceUnlockResult.UNLOCKED }
    }
}
