package dev.alllexey.itmowidgets.feature.weblogin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.weblogin.domain.Connection
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRepository
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** «Подключённые устройства»: the web sessions and agents with access to the account, each with «Отключить». */
class ConnectionsViewModel(
    private val repository: PairingRepository,
    private val time: AcademicTimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ConnectionsUiState>(ConnectionsUiState.Loading)
    val uiState: StateFlow<ConnectionsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value !is ConnectionsUiState.Content) _uiState.value = ConnectionsUiState.Loading
        viewModelScope.launch { load(message = null) }
    }

    fun askRevoke(id: Uuid) = _uiState.update { state ->
        if (state !is ConnectionsUiState.Content || state.revoking) return@update state
        state.copy(revoke = state.rows.firstOrNull { it.id == id })
    }

    fun dismissRevoke() = _uiState.update { state ->
        if (state is ConnectionsUiState.Content && !state.revoking) state.copy(revoke = null) else state
    }

    fun revoke() {
        val state = _uiState.value as? ConnectionsUiState.Content ?: return
        val row = state.revoke ?: return
        if (state.revoking) return
        _uiState.value = state.copy(revoking = true)
        viewModelScope.launch {
            when (val result = repository.revoke(row.id)) {
                is AppResult.Success -> load(message = UiText.Res(AccountStrings.connections_revoked))
                is AppResult.Failure -> _uiState.update {
                    val failed = result.error.toUiText()
                    (it as? ConnectionsUiState.Content)?.copy(revoke = null, revoking = false, message = failed) ?: it
                }
            }
        }
    }

    fun onMessageShown() = _uiState.update { state ->
        if (state is ConnectionsUiState.Content) state.copy(message = null) else state
    }

    private suspend fun load(message: UiText?) {
        _uiState.value = when (val result = repository.connections()) {
            is AppResult.Success -> ConnectionsUiState.Content(result.value.map { it.toRow() }, message = message)
            is AppResult.Failure -> (_uiState.value as? ConnectionsUiState.Content)
                ?.copy(revoke = null, revoking = false, message = message ?: result.error.toUiText())
                ?: ConnectionsUiState.Error(result.error.toUiText())
        }
    }

    private fun Connection.toRow(): ConnectionRow {
        val title = when (kind) {
            ConnectionKind.WEB -> browserText(userAgent)
            ConnectionKind.AGENT -> name?.let(UiText::Dynamic) ?: UiText.Res(AccountStrings.connections_agent_unnamed)
            ConnectionKind.OTHER -> name?.let(UiText::Dynamic) ?: UiText.Res(AccountStrings.connections_other)
        }
        return ConnectionRow(
            id = id,
            kind = kind,
            title = title,
            connected = UiText.Res(
                AccountStrings.connections_connected,
                listOf(createdAt.toLocalDateTime(time.timeZone).date.format(DateTexts.DAY_MONTH)),
            ),
            lastUsed = lastUsedAt?.let {
                UiText.Res(
                    AccountStrings.connections_last_used,
                    listOf(it.toLocalDateTime(time.timeZone).format(DateTexts.DAY_SHORT_MONTH_TIME)),
                )
            },
            itmoSession = itmoSession,
            revokeTitle = UiText.Res(AccountStrings.connections_revoke_title, listOf(title)),
            revokeText = UiText.Res(
                if (itmoSession) AccountStrings.connections_revoke_itmo else AccountStrings.connections_revoke_access,
            ),
        )
    }
}
