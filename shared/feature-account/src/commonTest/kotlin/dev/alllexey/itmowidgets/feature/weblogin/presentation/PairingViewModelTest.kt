package dev.alllexey.itmowidgets.feature.weblogin.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingError
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingHandoff
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingSealer
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Device pairing in the «Подключить устройство» sheet: a link's card, the hand-off and every refusal. */
class PairingViewModelTest {
    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val link = checkNotNull(PairLink.parse(LINK))
    private val request = PairingRequest(
        challengeId = Uuid.parse("0190f1a2-3b4c-7d5e-8f60-718293a4b5c6"),
        kind = PairingKind.AGENT,
        name = "nas",
        version = "0.1.0",
        userAgent = null,
        scopes = emptySet(),
        createdAt = Instant.parse("2026-09-24T09:04:30Z"),
    )
    private val web = NoWebLogin()
    private val pairing = FakePairingRepository()
    private val session = FakeSession()
    private val hpke = RecordingHpke()
    private val handoff = PairingHandoff(session, PairingSealer(hpke), SignedIn, FakeClock(Instant.parse("2026-10-12T09:00:00Z")))
    private val unlocked = DeviceUnlock { DeviceUnlockResult.UNLOCKED }

    @Test fun aScannedLinkShowsTheAgentWithItsVerificationCode() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val handle = SavedStateHandle()
        val vm = model(handle)

        vm.onScanned(LINK)
        assertEquals(WebLoginUiState.Checking("ABCD2345"), vm.uiState.value)
        runCurrent()

        val confirm = vm.uiState.value as WebLoginUiState.Confirm
        val sas = PairingCrypto.verificationCode(request.challengeId, link.publicKey, link.secret)
        assertEquals(PairingKind.AGENT, confirm.kind)
        assertEquals(UiText.Res(AccountStrings.pair_title_agent), confirm.title)
        assertEquals(UiText.Res(AccountStrings.pair_device_name, listOf("nas")), confirm.name)
        assertEquals(UiText.Res(AccountStrings.pair_version, listOf("0.1.0")), confirm.client)
        assertEquals(UiText.Res(AccountStrings.web_login_requested_at, listOf("12:04")), confirm.requestedAt)
        assertEquals(UiText.Res(AccountStrings.pair_access_itmo), confirm.access)
        assertEquals(UiText.Res(AccountStrings.pair_verification, listOf("${sas.take(3)} ${sas.drop(3)}")), confirm.verification)
        assertEquals(UiText.Res(AccountStrings.pair_own_only), confirm.warning)
        assertEquals(UiText.Res(AccountStrings.pair_approve), confirm.approve)
        assertEquals(AgentScope.entries, confirm.scopes.map { it.scope })
        assertTrue(confirm.scopesEditable && confirm.sharedSession && confirm.scopes.all { it.checked })
        assertEquals(listOf("open:ABCD2345:link"), pairing.calls)
        // The key never reaches the saved state.
        assertEquals("", handle.get<String>("web_login_code"))
    }

    @Test fun approvingUnlocksRotatesSealsAndSendsTheChosenScopes() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val unlock = CompletableDeferred<DeviceUnlockResult>()
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        vm.onScopeChanged(AgentScope.FRIENDS_READ, checked = false)
        vm.approve { unlock.await() }
        runCurrent()
        assertEquals(WebLoginUiState.Unlocking, vm.uiState.value)
        assertEquals(0, session.rotations)

        unlock.complete(DeviceUnlockResult.UNLOCKED)
        runCurrent()

        assertEquals(WebLoginUiState.Done(UiText.Res(AccountStrings.pair_done)), vm.uiState.value)
        assertEquals(1, session.rotations)
        assertTrue(hpke.plaintext!!.decodeToString().contains("\"isu\":100001"))
        assertEquals(
            listOf("open:ABCD2345:link", "approve:${request.challengeId}:[SPORT, SCHEDULE_WRITE]"),
            pairing.calls,
        )
    }

    @Test fun anAgentNeedsAtLeastOnePermission() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        AgentScope.entries.forEach { vm.onScopeChanged(it, checked = false) }
        assertFalse((vm.uiState.value as WebLoginUiState.Confirm).canApprove)
        vm.approve(unlocked)
        runCurrent()

        assertTrue(vm.uiState.value is WebLoginUiState.Confirm)
        assertEquals(0, session.rotations)
    }

    @Test fun anAgentSessionShowsItsPermissionsReadOnlyAndSendsNone() = runTest(main.dispatcher) {
        val renewal = request.copy(kind = PairingKind.AGENT_SESSION, scopes = setOf(AgentScope.SPORT))
        pairing.opens["ABCD2345"] = PairingResult.Success(renewal)
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        val confirm = vm.uiState.value as WebLoginUiState.Confirm
        assertEquals(UiText.Res(AccountStrings.pair_title_agent_session), confirm.title)
        assertEquals(listOf(AgentScope.SPORT), confirm.scopes.map { it.scope })
        assertFalse(confirm.scopesEditable)
        vm.onScopeChanged(AgentScope.SPORT, checked = false)
        assertTrue((vm.uiState.value as WebLoginUiState.Confirm).scopes.single().checked)
    }

    @Test fun aCancelledUnlockReturnsToTheCardWithoutRotating() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()
        val confirm = vm.uiState.value

        vm.approve { DeviceUnlockResult.CANCELLED }
        runCurrent()

        assertEquals(confirm, vm.uiState.value)
        assertEquals(0, session.rotations)
        assertEquals(listOf("open:ABCD2345:link"), pairing.calls)
    }

    @Test fun withoutAScreenLockNothingLeavesThePhoneAndRetryOpensTheLinkAgain() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        vm.approve { DeviceUnlockResult.NOT_SET_UP }
        runCurrent()
        assertEquals(UiText.Res(AccountStrings.pair_lock_required), (vm.uiState.value as WebLoginUiState.Error).text)
        assertEquals(0, session.rotations)

        vm.retry()
        runCurrent()
        assertTrue(vm.uiState.value is WebLoginUiState.Confirm)
        assertEquals(listOf("open:ABCD2345:link", "open:ABCD2345:link"), pairing.calls)
    }

    @Test fun aRejectedRotationAsksToSignInAgainAndSendsNothing() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        session.result = AppResult.Failure(AppError.Unauthorized)
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        vm.approve(unlocked)
        runCurrent()

        assertEquals(WebLoginUiState.Error(UiText.Res(AccountStrings.pair_sign_in_again), ""), vm.uiState.value)
        assertNull(hpke.plaintext)
        assertEquals(listOf("open:ABCD2345:link"), pairing.calls)
    }

    @Test fun cancellingTheCardRejectsTheDevice() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        vm.editCode()
        runCurrent()

        assertEquals(WebLoginUiState.Input(""), vm.uiState.value)
        assertEquals(listOf("open:ABCD2345:link", "reject:${request.challengeId}"), pairing.calls)
    }

    @Test fun everyRefusalOfTheLinkHasItsOwnTextAndNoRetry() = runTest(main.dispatcher) {
        val cases = mapOf(
            PairingError.LinkMismatch to AccountStrings.pair_link_mismatch,
            PairingError.Unsupported to AccountStrings.pair_unsupported,
            PairingError.TooManyDevices to AccountStrings.pair_too_many,
            PairingError.Failed(AppError.Forbidden) to AccountStrings.pair_agent_unavailable,
            PairingError.Failed(AppError.NotFound) to AccountStrings.web_login_not_found,
        )
        for ((error, text) in cases) {
            pairing.opens["ABCD2345"] = PairingResult.Failure(error)
            val vm = model()

            vm.onScanned(LINK)
            runCurrent()

            assertEquals(WebLoginUiState.Error(UiText.Res(text), ""), vm.uiState.value, error.toString())
        }
    }

    @Test fun aFailedApprovalKeepsTheLinkForARetryButNeverInTheField() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        pairing.approval = PairingResult.Failure(PairingError.Failed(AppError.Network))
        val vm = model()
        vm.onScanned(LINK)
        runCurrent()

        vm.approve(unlocked)
        runCurrent()
        assertEquals(LINK, (vm.uiState.value as WebLoginUiState.Error).code)

        vm.editCode()
        assertEquals(WebLoginUiState.Input(""), vm.uiState.value)
    }

    @Test fun aPastedLinkIsSubmittedAsALink() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Success(request)
        val vm = model()

        vm.onCodeChanged(" $LINK ")
        vm.submit()
        runCurrent()

        assertTrue(vm.uiState.value is WebLoginUiState.Confirm)
        assertEquals(listOf("open:ABCD2345:link"), pairing.calls)
    }

    @Test fun aTypedCodeOfADeviceIsRefusedWithAHintToScan() = runTest(main.dispatcher) {
        pairing.opens["ABCD2345"] = PairingResult.Failure(PairingError.KeyRequired)
        val vm = model()
        vm.onCodeChanged("abcd-2345")

        vm.submit()
        runCurrent()

        assertEquals(WebLoginUiState.Input("ABCD2345", UiText.Res(AccountStrings.pair_key_required)), vm.uiState.value)
        assertEquals(listOf("open:ABCD2345:typed"), pairing.calls)
        assertEquals(0, session.rotations)
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle()) =
        WebLoginViewModel(handle, web, pairing, handoff, FixedAcademicTime())

    /** Old web login routes do not know a device's code. */
    private class NoWebLogin : WebLoginRepository {
        override suspend fun preview(code: String): AppResult<WebLoginPreview> = AppResult.Failure(AppError.NotFound)

        override suspend fun approve(challengeId: Uuid): AppResult<Unit> = AppResult.Failure(AppError.NotFound)
    }

    private companion object {
        const val LINK = "https://widgets.alllexey.dev/pair#v1.ABCD2345." +
            "QxDul9iMwfCIpVdsd6sM9cOseX89lROcbIS1QpxZZio.AAECAwQFBgcICQoLDA0ODw"
    }
}
