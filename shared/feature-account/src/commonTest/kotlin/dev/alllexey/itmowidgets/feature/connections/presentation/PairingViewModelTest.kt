package dev.alllexey.itmowidgets.feature.connections.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.connections.domain.ClientItmoSession
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.HpkeSender
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoGrants
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoIdentity
import dev.alllexey.itmowidgets.feature.connections.domain.PairingError
import dev.alllexey.itmowidgets.feature.connections.domain.PairingKind
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import dev.alllexey.itmowidgets.feature.connections.domain.PairingSealer
import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun aNewAgentIsConfirmedWithTheCautionAndItsRequestedScopes() = runTest(main.dispatcher) {
        val model = viewModel()

        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()

        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertTrue(confirm.caution)
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
        assertFalse(page.readIdentity)
        assertFalse(model.isSignInCallback("https://id.itmo.ru/auth/login"))
        assertTrue(model.isSignInCallback(FakeClientSignIn.CALLBACK + "?code=c&state=s"))
        model.onSignInCallback(FakeClientSignIn.CALLBACK + "?code=c&state=s", null)
        advanceUntilIdle()

        val result = assertIs<PairingUiState.Result>(model.uiState.value)
        assertTrue(result.success)
        val approval = pairing.approvals.single()
        assertEquals(listOf("SPORT"), approval.scopes)
        assertEquals("sid-new", approval.session?.sid)
        assertNotNull(approval.sealedBundle)
        assertTrue(""""sid":"sid-new"""" in sealed.single().decodeToString())
        assertTrue(""""v":1""" in sealed.single().decodeToString())
        assertNull(approval.granted)
        assertEquals(listOf(false), signIn.identities)
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
        model.onSignInCallback(FakeClientSignIn.CALLBACK, null)
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
        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertTrue(confirm.scopes.isEmpty())
        assertTrue(confirm.caution)

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
    fun aWebRenewalHasACardWithoutTheCaution() = runTest(main.dispatcher) {
        pairing.opened = PairingResult.Success(
            Samples.request(client = ClientType.WEB, kind = PairingKind.RENEW, connectionId = Samples.agentId),
        )
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()

        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertFalse(confirm.renewal)
        assertFalse(confirm.caution)
    }

    @Test
    fun aRenewalGoesStraightToTheLockAndEndsTheOldSession() = runTest(main.dispatcher) {
        pairing.opened = PairingResult.Success(Samples.request(kind = PairingKind.RENEW, connectionId = Samples.agentId))
        devices.state.value = Devices(listOf(Samples.agent(sid = "sid-old")), emptyList())
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertTrue(confirm.renewal)
        // A renewal opens only for the owner's own live connection: nothing to warn about.
        assertFalse(confirm.caution)

        model.proceed(UNLOCKED)
        advanceUntilIdle()
        assertIs<PairingUiState.SigningIn>(model.uiState.value)
        model.onSignInCallback(FakeClientSignIn.CALLBACK, null)
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
        model.onSignInCallback(FakeClientSignIn.CALLBACK, null)
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
        assertFalse(model.isSignInCallback(FakeClientSignIn.CALLBACK))
        assertTrue(pairing.approvals.isEmpty())
    }

    private val identity = ItmoIdentity("test-identity-cookie", Instant.parse("2027-01-25T09:00:00Z"))
    private val sessionWithIdentity = ClientItmoSession(
        isu = 100001,
        sid = "sid-new",
        expiresAt = Instant.parse("2027-01-20T09:00:00Z"),
        refreshToken = "test-refresh-token",
        refreshExpiresAt = Instant.parse("2026-11-06T09:00:00Z"),
        identity = identity,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.openGrants(request: PairingRequest): PairingViewModel {
        pairing.opened = PairingResult.Success(request)
        val model = viewModel()
        model.onScanned(PairingVectors.LINK)
        advanceUntilIdle()
        return model
    }

    private fun PairingUiState.Confirm.switch(id: String): GrantSwitch = grants.flatMap { it.grants }.single { it.id == id }

    @Test
    fun aProtocol2CardGroupsTheGrantsAndKeepsSsoOffWithItsWarning() = runTest(main.dispatcher) {
        val model = openGrants(Samples.mcpRequest())

        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertTrue(confirm.caution)
        assertTrue(confirm.scopes.isEmpty())
        assertEquals(
            listOf(UiText.Res(AccountStrings.pair_group_widgets), UiText.Res(AccountStrings.pair_group_itmo)),
            confirm.grants.map { it.title },
        )
        assertEquals(
            listOf(listOf("widgets:reviews:read", "widgets:links:read"), listOf(ItmoGrants.REFRESH, ItmoGrants.SSO)),
            confirm.grants.map { group -> group.grants.map { it.id } },
        )
        val widgets = confirm.switch("widgets:reviews:read")
        assertEquals(UiText.Dynamic("widgets:reviews:read title"), widgets.title)
        assertNull(widgets.purpose)
        val refresh = confirm.switch(ItmoGrants.REFRESH)
        assertTrue(refresh.checked && refresh.enabled)
        assertEquals(UiText.Res(AccountStrings.grant_refresh_title), refresh.title)
        assertEquals(UiText.Res(AccountStrings.grant_purpose, listOf("${ItmoGrants.REFRESH} purpose")), refresh.purpose)
        val sso = confirm.switch(ItmoGrants.SSO)
        assertFalse(sso.checked)
        assertTrue(sso.enabled)
        assertEquals(UiText.Res(AccountStrings.grant_sso_warning), sso.warning)
        assertEquals(UiText.Res(AccountStrings.pair_continue), confirm.primary)
        assertTrue(confirm.canProceed)
    }

    @Test
    fun requiredGrantsAreLockedOnAndAnUnknownItmoGrantIsUnavailable() = runTest(main.dispatcher) {
        val model = openGrants(
            Samples.mcpRequest(
                grants = listOf(
                    Samples.grant("widgets:sport"),
                    Samples.grant(ItmoGrants.REFRESH, required = true),
                    Samples.grant("itmo:refresh:dev-stand"),
                ),
            ),
        )
        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)

        val refresh = confirm.switch(ItmoGrants.REFRESH)
        assertTrue(refresh.checked)
        assertFalse(refresh.enabled)
        assertEquals(UiText.Res(AccountStrings.grant_required), refresh.note)
        val unknown = confirm.switch("itmo:refresh:dev-stand")
        assertFalse(unknown.checked || unknown.enabled)
        assertEquals(UiText.Res(AccountStrings.grant_other_title), unknown.title)
        assertEquals(UiText.Res(AccountStrings.grant_unavailable), unknown.note)

        model.onGrantChanged(ItmoGrants.REFRESH, false)
        model.onGrantChanged("itmo:refresh:dev-stand", true)
        val unchanged = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertTrue(unchanged.switch(ItmoGrants.REFRESH).checked)
        assertFalse(unchanged.switch("itmo:refresh:dev-stand").checked)
    }

    @Test
    fun turningSsoOnAsksFirst() = runTest(main.dispatcher) {
        val model = openGrants(Samples.mcpRequest())

        model.onGrantChanged(ItmoGrants.SSO, true)
        val asked = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertEquals(UiText.Res(AccountStrings.grant_sso_confirm_text, listOf("MacBook Air")), asked.ask?.text)
        assertFalse(asked.switch(ItmoGrants.SSO).checked)
        model.dismissGrant()
        assertFalse(assertIs<PairingUiState.Confirm>(model.uiState.value).switch(ItmoGrants.SSO).checked)

        model.onGrantChanged(ItmoGrants.SSO, true)
        model.confirmGrant()
        val confirmed = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertNull(confirmed.ask)
        assertTrue(confirmed.switch(ItmoGrants.SSO).checked)
        assertEquals(UiText.Res(AccountStrings.pair_note_client_full, listOf("MacBook Air", "MacBook Air")), confirmed.note)

        model.onGrantChanged(ItmoGrants.SSO, false)
        val off = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertFalse(off.switch(ItmoGrants.SSO).checked)
        assertEquals(UiText.Res(AccountStrings.pair_note_client, listOf("MacBook Air", "MacBook Air")), off.note)
    }

    @Test
    fun theCookieAndTheRefreshTokenAreSealedInABundleV2InRequestOrder() = runTest(main.dispatcher) {
        signIn.result = ClientSignInResult.Success(sessionWithIdentity)
        val model = openGrants(Samples.mcpRequest())
        model.onGrantChanged("widgets:links:read", false)
        model.onGrantChanged(ItmoGrants.SSO, true)
        model.confirmGrant()

        model.proceed(UNLOCKED)
        advanceUntilIdle()
        val page = assertIs<PairingUiState.SigningIn>(model.uiState.value)
        assertTrue(page.readIdentity)
        assertEquals(listOf(true), signIn.identities)
        model.onSignInCallback(FakeClientSignIn.CALLBACK, "test-identity-cookie")
        advanceUntilIdle()

        assertTrue(assertIs<PairingUiState.Result>(model.uiState.value).success)
        assertEquals(listOf<String?>("test-identity-cookie"), signIn.cookies)
        val approval = pairing.approvals.single()
        assertEquals(listOf("widgets:reviews:read", ItmoGrants.REFRESH, ItmoGrants.SSO), approval.granted)
        assertNull(approval.scopes)
        assertEquals("sid-new", approval.session?.sid)
        val bundle = Json.parseToJsonElement(sealed.single().decodeToString()).jsonObject
        assertEquals(2, bundle.getValue("v").jsonPrimitive.int)
        assertEquals("itmo-mcp", bundle.getValue("clientId").jsonPrimitive.content)
        val items = bundle.getValue("itmo").jsonObject.getValue("items").jsonArray.map { it.jsonObject }
        assertEquals(listOf(ItmoGrants.REFRESH, ItmoGrants.SSO), items.map { it.getValue("grant").jsonPrimitive.content })
        assertEquals(listOf("test-refresh-token", "test-identity-cookie"), items.map { it.getValue("value").jsonPrimitive.content })
        assertEquals("2027-01-25T09:00:00Z", items.last().getValue("expiresAt").jsonPrimitive.content)
    }

    @Test
    fun onlySsoDropsTheRefreshTokenWithoutEndingTheSession() = runTest(main.dispatcher) {
        signIn.result = ClientSignInResult.Success(sessionWithIdentity)
        val model = openGrants(Samples.mcpRequest())
        model.onGrantChanged(ItmoGrants.REFRESH, false)
        model.onGrantChanged(ItmoGrants.SSO, true)
        model.confirmGrant()
        model.proceed(UNLOCKED)
        advanceUntilIdle()
        model.onSignInCallback(FakeClientSignIn.CALLBACK, "test-identity-cookie")
        advanceUntilIdle()

        val plaintext = sealed.single().decodeToString()
        assertFalse("test-refresh-token" in plaintext)
        assertTrue("test-identity-cookie" in plaintext)
        assertEquals(listOf("widgets:reviews:read", "widgets:links:read", ItmoGrants.SSO), pairing.approvals.single().granted)
        assertTrue(logins.ended.isEmpty())
    }

    @Test
    fun withoutItmoGrantsThereIsNoSignInAndNoBundle() = runTest(main.dispatcher) {
        val model = openGrants(Samples.mcpRequest())
        model.onGrantChanged(ItmoGrants.REFRESH, false)
        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertEquals(UiText.Res(AccountStrings.pair_connect), confirm.primary)
        assertEquals(UiText.Res(AccountStrings.pair_note_widgets, listOf("MacBook Air")), confirm.note)

        model.proceed(UNLOCKED)
        advanceUntilIdle()

        assertTrue(assertIs<PairingUiState.Result>(model.uiState.value).success)
        assertEquals(0, signIn.begun)
        val approval = pairing.approvals.single()
        assertNull(approval.sealedBundle)
        assertNull(approval.session)
        assertEquals(listOf("widgets:reviews:read", "widgets:links:read"), approval.granted)
        assertTrue(sealed.isEmpty())
    }

    @Test
    fun aNewConnectionNeedsAWidgetsGrant() = runTest(main.dispatcher) {
        val model = openGrants(Samples.mcpRequest())

        model.onGrantChanged("widgets:reviews:read", false)
        model.onGrantChanged("widgets:links:read", false)

        assertFalse(assertIs<PairingUiState.Confirm>(model.uiState.value).canProceed)
    }

    @Test
    fun aRenewalShowsOnlyTheItmoGrantsNotHeldAndKeepsTheHeldOnes() = runTest(main.dispatcher) {
        val request = Samples.mcpRequest(
            kind = PairingKind.RENEW,
            connectionId = Samples.agentId,
            grants = listOf(Samples.grant(ItmoGrants.REFRESH, held = true), Samples.grant(ItmoGrants.SSO, held = false)),
        )
        val model = openGrants(request)
        val confirm = assertIs<PairingUiState.Confirm>(model.uiState.value)
        assertFalse(confirm.renewal)
        assertFalse(confirm.caution)
        assertEquals(listOf(listOf(ItmoGrants.SSO)), confirm.grants.map { group -> group.grants.map { it.id } })

        model.proceed(UNLOCKED)
        advanceUntilIdle()
        assertEquals(listOf(false), signIn.identities)
        model.onSignInCallback(FakeClientSignIn.CALLBACK, null)
        advanceUntilIdle()

        assertEquals(listOf(ItmoGrants.REFRESH), pairing.approvals.single().granted)
    }

    @Test
    fun aRenewalOfHeldGrantsHasNoCard() = runTest(main.dispatcher) {
        val request = Samples.mcpRequest(
            kind = PairingKind.RENEW,
            connectionId = Samples.agentId,
            grants = listOf(Samples.grant(ItmoGrants.REFRESH, held = true), Samples.grant(ItmoGrants.SSO, held = true)),
        )
        val model = openGrants(request)

        assertTrue(assertIs<PairingUiState.Confirm>(model.uiState.value).renewal)
        model.proceed(UNLOCKED)
        advanceUntilIdle()
        assertEquals(listOf(true), signIn.identities)
    }

    @Test
    fun aMissingCookieFailsAndOffersASignInAgain() = runTest(main.dispatcher) {
        signIn.result = ClientSignInResult.IdentityMissing
        val model = openGrants(Samples.mcpRequest())
        model.onGrantChanged(ItmoGrants.SSO, true)
        model.confirmGrant()
        model.proceed(UNLOCKED)
        advanceUntilIdle()
        model.onSignInCallback(FakeClientSignIn.CALLBACK, null)
        advanceUntilIdle()

        val result = assertIs<PairingUiState.Result>(model.uiState.value)
        assertFalse(result.success)
        assertEquals(UiText.Res(AccountStrings.pair_identity_missing), result.text)
        assertEquals(ResultAction.SIGN_IN_AGAIN, result.next)
        assertTrue(pairing.approvals.isEmpty())
    }

    private companion object {
        val UNLOCKED = DeviceUnlock { DeviceUnlockResult.UNLOCKED }
    }
}
