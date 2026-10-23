package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.connections.SignInLabel
import dev.alllexey.itmowidgets.core.connections.ThisPhone
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class DevicesViewModelTest {

    private val main = TestMainDispatcher()
    private val revoked = Samples.agent(id = Samples.revokedId, sid = "sid-revoked", revokedAt = Instant.parse("2026-10-04T09:00:00Z"))
    private val backend = Devices(
        connections = listOf(Samples.agent(), Samples.web(), revoked),
        installs = listOf(
            Samples.install(Samples.phoneId, current = true, sid = "sid-phone"),
            Samples.install(Samples.tabletId, current = false, sid = "sid-gone"),
        ),
    )
    private val devices = FakeDevicesRepository().apply { next = backend }
    private val logins = FakeItmoLogins(
        AppResult.Success(
            ItmoLogins.Listed(
                sessions = listOf(
                    Samples.login("sid-phone", "Chrome Mobile WebView, Android"),
                    Samples.login("sid-agent", "Chrome 155, Linux"),
                    Samples.login("sid-revoked"),
                    Samples.login("sid-other-1"),
                    Samples.login("sid-other-2", "Safari, iOS"),
                ),
                ownSid = "sid-phone",
            ),
        ),
    )
    private val gate = FakeBackendGate(optedIn = true)
    private val label = object : SignInLabel {
        val value = MutableStateFlow(true)
        override fun observeBrowserLike(): Flow<Boolean> = value
        override suspend fun isBrowserLike() = value.value
        override suspend fun setBrowserLike(browserLike: Boolean) { value.value = browserLike }
    }
    private val phone = ThisPhone("Google Pixel 8", "2.4.0")

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun viewModel() = DevicesViewModel(devices, logins, gate, label, phone, FixedAcademicTime())

    @Test
    fun installsClientsAndOtherSessionsAreJoinedBySid() = runTest(main.dispatcher) {
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertEquals(UiText.Dynamic("Google Pixel 8"), state.thisPhone?.title)
        val tablet = state.installs.single()
        assertTrue(tablet.subtitle.toString().contains("devices_sign_in_ended"), "a sid ITMO.ID no longer lists")
        assertEquals(listOf(Samples.agentId.toString(), Samples.webId.toString()), state.clients.map { it.connectionId })
        val pill = assertNotNull(state.clients.first().pill, "the agent's sign-in ends in 4 days")
        assertFalse(pill.error)
        val others = assertIs<OtherLogins.Content>(state.others)
        assertEquals(listOf("session:sid-other-1", "session:sid-other-2"), others.rows.map { it.key })
    }

    @Test
    fun aRevokedClientsOpenSessionIsEndedAndReported() = runTest(main.dispatcher) {
        viewModel()
        advanceUntilIdle()

        assertTrue(listOf("sid-revoked") in logins.ended)
        assertTrue("ended ${Samples.revokedId}" in devices.calls)
    }

    @Test
    fun endingAllOtherSessionsAsksFirstAndEndsOnlyThem() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        logins.ended.clear()

        model.askEndAllOthers()
        assertNotNull(model.uiState.value.confirm)
        model.confirm()
        advanceUntilIdle()

        assertEquals(listOf(listOf("sid-other-1", "sid-other-2")), logins.ended)
        assertNull(model.uiState.value.confirm)
        assertNotNull(model.uiState.value.message)
    }

    @Test
    fun signingOutAnInstallEndsItsSessionThenItsPush() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        logins.ended.clear()

        model.open("install:${Samples.tabletId}")
        assertNotNull(model.uiState.value.sheet?.action)
        model.askSheetAction()
        model.confirm()
        advanceUntilIdle()

        assertEquals(listOf(listOf("sid-gone")), logins.ended)
        assertTrue("signOut ${Samples.tabletId}" in devices.calls)
        assertNull(model.uiState.value.sheet)
    }

    @Test
    fun thisPhonesSheetHasNoButton() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.open("install:${Samples.phoneId}")

        val sheet = assertNotNull(model.uiState.value.sheet)
        assertNull(sheet.action)
        assertNotNull(sheet.note)
    }

    @Test
    fun withoutTheServerOnlyTheItmoPartShows() = runTest(main.dispatcher) {
        gate.optedIn.value = false
        val state = viewModel().also { advanceUntilIdle() }.uiState.value

        assertFalse(state.backend)
        assertTrue(state.clients.isEmpty() && state.installs.isEmpty())
        assertEquals(UiText.Dynamic("Google Pixel 8"), state.thisPhone?.title)
        assertFalse("refresh" in devices.calls)
        assertIs<OtherLogins.Content>(state.others)
    }

    @Test
    fun withoutACookieTheSectionIsANote() = runTest(main.dispatcher) {
        logins.answer = AppResult.Success(ItmoLogins.NoCookie)

        assertEquals(OtherLogins.NoCookie, viewModel().also { advanceUntilIdle() }.uiState.value.others)
    }

    @Test
    fun theLabelSwitchWritesTheSetting() = runTest(main.dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.setBrowserLike(false)
        advanceUntilIdle()

        assertFalse(label.value.value)
        assertFalse(model.uiState.value.browserLike)
    }
}
