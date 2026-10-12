package dev.alllexey.itmowidgets.feature.weblogin.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.weblogin.domain.Connection
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ConnectionsViewModelTest {
    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val repository = FakePairingRepository()
    private val agent = Connection(
        id = Uuid.parse("00000000-0000-4000-8000-000000000601"),
        kind = ConnectionKind.AGENT,
        name = "nas",
        userAgent = null,
        createdAt = Instant.parse("2026-10-12T09:00:00Z"),
        lastUsedAt = Instant.parse("2026-10-12T11:05:00Z"),
        itmoSession = true,
    )
    private val web = Connection(
        id = Uuid.parse("00000000-0000-4000-8000-000000000501"),
        kind = ConnectionKind.WEB,
        name = null,
        userAgent = null,
        createdAt = Instant.parse("2026-10-01T09:00:00Z"),
        lastUsedAt = null,
        itmoSession = false,
    )

    @Test fun rowsNameTheDeviceAndSayWhatARevocationEnds() = runTest(main.dispatcher) {
        repository.connections = AppResult.Success(listOf(agent, web, agent.copy(id = Uuid.random(), name = null)))
        val vm = ConnectionsViewModel(repository, FixedAcademicTime())
        runCurrent()

        val rows = (vm.uiState.value as ConnectionsUiState.Content).rows
        val nas = rows[0]
        assertEquals(UiText.Dynamic("nas"), nas.title)
        // Moscow time, as every time the app shows.
        assertEquals(UiText.Res(AccountStrings.connections_connected, listOf("12 октября")), nas.connected)
        assertEquals(UiText.Res(AccountStrings.connections_last_used, listOf("12 окт., 14:05")), nas.lastUsed)
        assertEquals(UiText.Res(AccountStrings.connections_revoke_title, listOf(nas.title)), nas.revokeTitle)
        assertEquals(UiText.Res(AccountStrings.connections_revoke_itmo), nas.revokeText)
        assertEquals(UiText.Res(AccountStrings.web_login_browser_unknown), rows[1].title)
        assertNull(rows[1].lastUsed)
        assertEquals(UiText.Res(AccountStrings.connections_revoke_access), rows[1].revokeText)
        assertEquals(UiText.Res(AccountStrings.connections_agent_unnamed), rows[2].title)
    }

    @Test fun aConfirmedRevocationReloadsTheListAndSaysSo() = runTest(main.dispatcher) {
        repository.connections = AppResult.Success(listOf(agent, web))
        val vm = ConnectionsViewModel(repository, FixedAcademicTime())
        runCurrent()

        vm.askRevoke(agent.id)
        assertEquals(agent.id, (vm.uiState.value as ConnectionsUiState.Content).revoke?.id)
        repository.connections = AppResult.Success(listOf(web))
        vm.revoke()
        runCurrent()

        val content = vm.uiState.value as ConnectionsUiState.Content
        assertEquals(listOf(web.id), content.rows.map { it.id })
        assertNull(content.revoke)
        assertEquals(UiText.Res(AccountStrings.connections_revoked), content.message)
        assertEquals(listOf("connections", "revoke:${agent.id}", "connections"), repository.calls)
        vm.onMessageShown()
        assertNull((vm.uiState.value as ConnectionsUiState.Content).message)
    }

    @Test fun aDismissedConfirmationRevokesNothing() = runTest(main.dispatcher) {
        repository.connections = AppResult.Success(listOf(agent))
        val vm = ConnectionsViewModel(repository, FixedAcademicTime())
        runCurrent()

        vm.askRevoke(agent.id)
        vm.dismissRevoke()
        vm.revoke()
        runCurrent()

        assertEquals(listOf("connections"), repository.calls)
    }

    @Test fun aFailedRevocationKeepsTheRowAndShowsWhy() = runTest(main.dispatcher) {
        repository.connections = AppResult.Success(listOf(agent))
        repository.revocation = AppResult.Failure(AppError.Network)
        val vm = ConnectionsViewModel(repository, FixedAcademicTime())
        runCurrent()

        vm.askRevoke(agent.id)
        vm.revoke()
        runCurrent()

        val content = vm.uiState.value as ConnectionsUiState.Content
        assertEquals(listOf(agent.id), content.rows.map { it.id })
        assertEquals(AppError.Network.toUiText(), content.message)
    }

    @Test fun aFailedFirstLoadIsAnErrorThatRetries() = runTest(main.dispatcher) {
        repository.connections = AppResult.Failure(AppError.CustomServicesDisabled)
        val vm = ConnectionsViewModel(repository, FixedAcademicTime())
        runCurrent()
        assertEquals(ConnectionsUiState.Error(AppError.CustomServicesDisabled.toUiText()), vm.uiState.value)

        repository.connections = AppResult.Success(emptyList())
        vm.refresh()
        assertEquals(ConnectionsUiState.Loading, vm.uiState.value)
        runCurrent()
        assertEquals(ConnectionsUiState.Content(emptyList()), vm.uiState.value)
    }
}
