package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoGrants
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoHealth
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class ConnectedClientViewModelTest {

    private val main = TestMainDispatcher()
    private val devices = FakeDevicesRepository().apply { next = Devices(listOf(Samples.agent(), Samples.web()), emptyList()) }
    private val logins = FakeItmoLogins(AppResult.Success(ItmoLogins.Listed(listOf(Samples.login("sid-agent")), "sid-phone")))
    private var unlocks = 0

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun viewModel(id: String = Samples.agentId.toString()) =
        ConnectedClientViewModel(id, devices, logins, FixedAcademicTime())

    private fun unlock(result: DeviceUnlockResult) = DeviceUnlock { unlocks++; result }

    @Test
    fun anEndingSignInWarnsAndOffersRenewal() = runTest(main.dispatcher) {
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        val signIn = assertNotNull(state.signIn)
        assertEquals(SignInTone.WARNING, signIn.tone)
        assertNotNull(signIn.action)
        assertEquals(listOf("SPORT", "FRIENDS_READ"), state.scopes.map { it.id })
    }

    @Test
    fun aFineSignInOffersRenewalOnlyInItsLastTwoWeeks() = runTest(main.dispatcher) {
        devices.next = Devices(listOf(Samples.agent(expiresAt = Instant.parse("2026-12-30T09:00:00Z"))), emptyList())

        val signIn = assertNotNull(viewModel().also { advanceUntilIdle() }.uiState.value.signIn)

        assertEquals(SignInTone.OK, signIn.tone)
        assertNull(signIn.action)
    }

    @Test
    fun aClosedSignInIsAnError() = runTest(main.dispatcher) {
        devices.next = Devices(listOf(Samples.agent(health = ItmoHealth.CLOSED)), emptyList())

        assertEquals(SignInTone.ERROR, viewModel().also { advanceUntilIdle() }.uiState.value.signIn?.tone)
    }

    @Test
    fun narrowingAppliesAtOnceAndWideningAsksForTheLock() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onScopeChanged("FRIENDS_READ", true, unlock(DeviceUnlockResult.CANCELLED))
        advanceUntilIdle()
        assertEquals(1, unlocks)
        assertTrue(devices.calls.none { it.startsWith("scopes") })

        model.onScopeChanged("FRIENDS_READ", true, unlock(DeviceUnlockResult.UNLOCKED))
        advanceUntilIdle()
        assertTrue("scopes ${Samples.agentId} [SPORT, FRIENDS_READ]" in devices.calls)

        model.onScopeChanged("SPORT", false, unlock(DeviceUnlockResult.UNLOCKED))
        advanceUntilIdle()
        assertEquals(2, unlocks, "narrowing asks nothing")
        assertTrue("scopes ${Samples.agentId} [FRIENDS_READ]" in devices.calls)
    }

    @Test
    fun theLastScopeStaysOn() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.onScopeChanged("SPORT", false, unlock(DeviceUnlockResult.UNLOCKED))
        advanceUntilIdle()

        assertTrue(devices.calls.none { it.startsWith("scopes") })
    }

    @Test
    fun revokingEndsAccessThenTheItmoSessionAndLeaves() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.askRevoke()
        assertNotNull(model.uiState.value.confirm)
        model.revoke()
        advanceUntilIdle()

        assertTrue("revoke ${Samples.agentId}" in devices.calls)
        assertEquals(listOf(listOf("sid-agent")), logins.ended)
        assertTrue("ended ${Samples.agentId}" in devices.calls)
        assertTrue(model.uiState.value.closed)
    }

    @Test
    fun theWebHasNoSignInAndNoSwitches() = runTest(main.dispatcher) {
        val state = viewModel(Samples.webId.toString()).also { advanceUntilIdle() }.uiState.value

        assertNull(state.signIn)
        assertTrue(state.scopes.isEmpty())
        assertFalse(state.gone)
    }

    @Test
    fun anUnknownClientIsGone() = runTest(main.dispatcher) {
        assertTrue(viewModel("00000000-0000-4000-8000-000000000999").also { advanceUntilIdle() }.uiState.value.gone)
    }

    @Test
    fun theRenewalSheetNamesTheClientsCommand() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.askRenew()

        assertEquals("itmo-widgets-agent renew", model.uiState.value.renew?.command)
    }

    @Test
    fun anAgentCanGiveItsSignInBackAndIsToldItStopsWorking() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        assertFalse(model.uiState.value.fullItmoAccess)
        assertTrue(model.uiState.value.takeBack)
        model.askTakeBackItmo()
        assertEquals(
            UiText.Res(AccountStrings.connection_take_back_agent_text, listOf("nas")),
            model.uiState.value.confirm?.text,
        )
    }

    @Test
    fun anMcpServerWithoutFullAccessLosesOnlyItsMyItmoSignIn() = runTest(main.dispatcher) {
        devices.next = Devices(listOf(Samples.agent().copy(client = ClientType.MCP)), emptyList())
        val model = viewModel()
        advanceUntilIdle()

        model.askTakeBackItmo()
        assertEquals(
            UiText.Res(AccountStrings.connection_take_back_text, listOf("nas")),
            model.uiState.value.confirm?.text,
        )
    }

    @Test
    fun aClientWithoutAnItmoSignInIsNotClosedAndGetsOneThroughRenewal() = runTest(main.dispatcher) {
        devices.next = Devices(listOf(Samples.agent(itmoGrants = emptyList(), signedIn = false)), emptyList())
        val model = viewModel()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(SignInTone.OK, state.signIn?.tone)
        assertEquals(UiText.Res(AccountStrings.connection_sign_in_none), state.signIn?.title)
        assertEquals(false, state.signIn?.signedIn)
        assertFalse(state.takeBack)
        model.askTakeBackItmo()
        assertNull(model.uiState.value.confirm)
        model.askRenew()
        assertEquals(
            UiText.Res(AccountStrings.connection_sign_in_give_title, listOf("nas")),
            model.uiState.value.renew?.title,
        )
        model.askRevoke()
        assertEquals(UiText.Res(AccountStrings.connection_revoke_widgets_text), model.uiState.value.confirm?.text)
    }

    @Test
    fun takingFullAccessBackEndsTheSessionReportsItAndReloads() = runTest(main.dispatcher) {
        devices.next = Devices(listOf(Samples.agent(itmoGrants = listOf(ItmoGrants.REFRESH, ItmoGrants.SSO))), emptyList())
        val model = viewModel()
        advanceUntilIdle()
        assertTrue(model.uiState.value.fullItmoAccess)

        model.askTakeBackItmo()
        assertEquals(
            UiText.Res(AccountStrings.connection_take_back_full_text, listOf("nas")),
            model.uiState.value.confirm?.text,
        )
        devices.calls.clear()
        model.confirm()
        advanceUntilIdle()

        assertEquals(listOf(listOf("sid-agent")), logins.ended)
        assertEquals(listOf("closed ${Samples.agentId}", "refresh"), devices.calls)
        assertNull(model.uiState.value.confirm)
        assertFalse(model.uiState.value.closed)
    }

    @Test
    fun aSessionThatCouldNotBeEndedIsNotReportedClosed() = runTest(main.dispatcher) {
        devices.next = Devices(listOf(Samples.agent(itmoGrants = listOf(ItmoGrants.SSO))), emptyList())
        logins.endFailure = AppError.Unauthorized
        val model = viewModel()
        advanceUntilIdle()
        devices.calls.clear()

        model.askTakeBackItmo()
        model.confirm()
        advanceUntilIdle()

        assertTrue(devices.calls.isEmpty())
        assertNotNull(model.uiState.value.message)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun theRevokeDialogStillRevokes() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.askRevoke()
        model.confirm()
        advanceUntilIdle()

        assertTrue("revoke ${Samples.agentId}" in devices.calls)
        assertTrue(model.uiState.value.closed)
    }
}
