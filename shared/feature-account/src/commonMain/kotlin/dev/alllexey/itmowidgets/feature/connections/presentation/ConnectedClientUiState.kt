package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/**
 * A connected client (UX spec section 4). [gone] is a client that is not in the list (revoked elsewhere): the screen
 * says so; [closed] is set once this screen revoked it and the host leaves. [signIn] and [scopes] are an agent's
 * or an MCP server's; the web has neither.
 */
data class ConnectedClientUiState(
    val loading: Boolean = true,
    val gone: Boolean = false,
    val title: UiText = UiText.Dynamic(""),
    val subtitle: UiText? = null,
    val icon: DeviceIcon = DeviceIcon.COMPUTER,
    val signIn: SignInStatus? = null,
    val scopes: List<ScopeSwitch> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val revoke: UiText = UiText.Dynamic(""),
    val confirm: ConfirmAsk? = null,
    val renew: RenewSheet? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
    val closed: Boolean = false,
)

/** «Вход в ИТМО»: the status row in [tone] and the action row, absent while renewal is not due. */
data class SignInStatus(val tone: SignInTone, val title: UiText, val text: UiText?, val action: UiText?)

enum class SignInTone { OK, WARNING, ERROR }

/** «Обновить вход для nas»: the command to run on the client, which then shows a QR. */
data class RenewSheet(val title: UiText, val command: String, val note: UiText)
