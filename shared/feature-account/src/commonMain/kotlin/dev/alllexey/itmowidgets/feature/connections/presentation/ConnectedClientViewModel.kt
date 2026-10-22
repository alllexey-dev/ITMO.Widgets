package dev.alllexey.itmowidgets.feature.connections.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlockResult
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.SignInAlert
import dev.alllexey.itmowidgets.feature.connections.domain.holdsItmo
import dev.alllexey.itmowidgets.feature.connections.domain.signInAlert
import dev.alllexey.itmowidgets.shared.feature.account.AccountPlurals
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One connected client: its ITMO sign-in, scopes as switches (narrowing at once, widening behind the screen lock),
 * the facts and «Отключить», which ends Backend access and then the client's ITMO session with the phone's own cookie
 * (plan 6.2). The client comes from [DevicesRepository]'s last answer, refreshed on open.
 */
class ConnectedClientViewModel(
    private val id: String,
    private val devices: DevicesRepository,
    private val logins: ItmoLoginsRepository,
    private val time: AcademicTimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectedClientUiState())
    val uiState: StateFlow<ConnectedClientUiState> = _uiState.asStateFlow()

    private val uuid = runCatching { Uuid.parse(id) }.getOrNull()
    private var itmo: ItmoLogins? = null

    init {
        devices.devices.onEach { render() }.launchIn(viewModelScope)
        viewModelScope.launch {
            val refreshed = devices.refresh()
            itmo = (logins.list() as? AppResult.Success)?.value
            if (refreshed is AppResult.Failure && devices.devices.value == null) {
                _uiState.update { it.copy(loading = false, message = refreshed.error.toUiText()) }
            }
            render()
        }
    }

    /** Off applies at once; on asks for [unlock] first. At least one scope stays on. */
    fun onScopeChanged(scopeId: String, checked: Boolean, unlock: DeviceUnlock) {
        val connection = connection() ?: return
        if (_uiState.value.busy) return
        val granted = connection.scopes.filter { it.granted }.mapNotNull { it.scope.id }.toSet()
        val next = if (checked) granted + scopeId else granted - scopeId
        if (next.isEmpty() || next == granted) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            if (checked && unlock.unlock() != DeviceUnlockResult.UNLOCKED) {
                _uiState.update { it.copy(busy = false) }
                return@launch
            }
            val ordered = connection.scopes.mapNotNull { it.scope.id }.filter { it in next }
            val result = devices.updateScopes(connection.id, ordered)
            _uiState.update {
                it.copy(
                    busy = false,
                    message = when (result) {
                        is AppResult.Success -> UiText.Res(AccountStrings.connection_scopes_changed)
                        is AppResult.Failure -> result.error.toUiText()
                    },
                )
            }
        }
    }

    fun askRenew() {
        val connection = connection() ?: return
        if (!connection.holdsItmo) return
        _uiState.update {
            it.copy(
                renew = RenewSheet(
                    title = UiText.Res(AccountStrings.connection_renew_title, listOf(connection.name)),
                    command = if (connection.client == ClientType.MCP) MCP_RENEW else AGENT_RENEW,
                    note = UiText.Res(AccountStrings.connection_renew_note, listOf(connection.name)),
                ),
            )
        }
    }

    fun closeRenew() = _uiState.update { it.copy(renew = null) }

    fun askRevoke() {
        val connection = connection() ?: return
        val web = connection.client == ClientType.WEB
        val noCookie = itmo !is ItmoLogins.Listed
        _uiState.update {
            it.copy(
                confirm = ConfirmAsk(
                    title = UiText.Res(
                        if (web) AccountStrings.connection_sign_out_web_title else AccountStrings.connection_revoke_title,
                        listOf(connection.name),
                    ),
                    text = UiText.Res(
                        when {
                            web -> AccountStrings.connection_revoke_web_text
                            noCookie -> AccountStrings.connection_revoke_text_later
                            else -> AccountStrings.connection_revoke_text
                        },
                    ),
                    confirm = it.revoke,
                ),
            )
        }
    }

    fun dismissConfirm() {
        if (!_uiState.value.busy) _uiState.update { it.copy(confirm = null) }
    }

    fun revoke() {
        val connection = connection() ?: return
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = devices.revoke(connection.id)) {
                is AppResult.Failure -> _uiState.update {
                    it.copy(busy = false, confirm = null, message = result.error.toUiText())
                }
                is AppResult.Success -> {
                    // Best effort: a failure here leaves the session to «Устройства»' reconciliation.
                    connection.itmo?.sid?.let { sid ->
                        if (logins.end(listOf(sid)) is AppResult.Success) devices.itmoEnded(connection.id)
                    }
                    _uiState.update { it.copy(busy = false, confirm = null, closed = true) }
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    private fun connection(): Connection? = devices.devices.value?.connections?.firstOrNull { it.id == uuid }

    private fun render() {
        val known = devices.devices.value ?: return
        val connection = known.connections.firstOrNull { it.id == uuid && it.revokedAt == null }
        if (connection == null) {
            if (!_uiState.value.closed) _uiState.update { it.copy(loading = false, gone = true) }
            return
        }
        val now = time.now()
        val zone = time.timeZone
        val session = (itmo as? ItmoLogins.Listed)?.sessions?.firstOrNull { it.sid == connection.itmo?.sid }
        val web = connection.client == ClientType.WEB
        _uiState.update { state ->
            state.copy(
                loading = false,
                gone = false,
                title = UiText.Dynamic(connection.name),
                subtitle = clientSubtitle(connection.client, connection.version, connection.platform),
                icon = connection.client.icon(),
                signIn = if (connection.holdsItmo) signIn(connection) else null,
                scopes = if (web) {
                    emptyList()
                } else {
                    connection.scopes.map {
                        ScopeSwitch(it.scope.id, UiText.Dynamic(it.scope.title), UiText.Dynamic(it.scope.description), it.granted)
                    }
                },
                facts = buildList {
                    add(Fact(UiText.Res(AccountStrings.connection_fact_connected), dayMonth(connection.createdAt, zone)))
                    connection.lastUsedAt?.let {
                        add(Fact(UiText.Res(AccountStrings.devices_fact_activity), activityText(it, now, zone)))
                    }
                    session?.let { add(Fact(UiText.Res(AccountStrings.connection_fact_label), UiText.Dynamic(it.label))) }
                    session?.ipAddress?.let { add(Fact(UiText.Res(AccountStrings.devices_fact_ip), UiText.Dynamic(it))) }
                    if (web) add(Fact(UiText.Res(AccountStrings.connection_fact_approved), dayMonth(connection.approvedAt, zone)))
                },
                revoke = UiText.Res(if (web) AccountStrings.connection_sign_out_web else AccountStrings.connection_revoke),
            )
        }
    }

    private fun signIn(connection: Connection): SignInStatus {
        val now = time.now()
        val ends = connection.itmo?.expiresAt
        return when (val alert = connection.signInAlert(now)) {
            SignInAlert.Closed -> SignInStatus(
                tone = SignInTone.ERROR,
                title = UiText.Res(AccountStrings.connection_sign_in_closed),
                text = UiText.Res(AccountStrings.connection_sign_in_closed_text, listOf(connection.name)),
                action = UiText.Res(AccountStrings.connection_sign_in_again),
            )
            is SignInAlert.Ending -> SignInStatus(
                tone = SignInTone.WARNING,
                title = UiText.Res(AccountStrings.connection_sign_in_until, listOf(dayMonth(alert.at, time.timeZone))),
                text = (alert.at - now).inWholeDays.toInt().coerceAtLeast(1).let { days ->
                    UiText.Plural(AccountPlurals.connection_sign_in_ending, days, listOf(days, connection.name))
                },
                action = UiText.Res(AccountStrings.connection_renew),
            )
            null -> SignInStatus(
                tone = SignInTone.OK,
                title = UiText.Res(AccountStrings.connection_sign_in_until, listOf(dayMonth(checkNotNull(ends), time.timeZone))),
                text = null,
                action = UiText.Res(AccountStrings.connection_renew).takeIf { ends - now <= RENEW_WINDOW },
            )
        }
    }

    private companion object {
        const val AGENT_RENEW = "itmo-widgets-agent renew"
        const val MCP_RENEW = "itmo-mcp renew"
    }
}
