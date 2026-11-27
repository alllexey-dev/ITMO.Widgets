package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/** «Подключить устройство» (UX spec section 5). */
sealed interface PairingUiState {
    /** Scan or paste a link; [error] says why the last one was not taken. */
    data class Input(val text: String = "", val error: UiText? = null) : PairingUiState

    data object Opening : PairingUiState

    /**
     * The client to approve. [scopes] are switches of a new agent or MCP server of protocol 1 (narrowing only);
     * [grants] are the switches of a protocol-2 request, grouped «Виджеты» and «ИТМО» (ADR 0040), and [ask] the
     * confirmation of turning `itmo:sso` on. [caution] asks a new connection to be approved only for a QR on the user's
     * own screen; [renewal] has no card: the host asks for the screen lock at once ([PairingViewModel.proceed]).
     * [ready] is false while the choice breaks Backend's rules (no Widgets grant on a new connection).
     */
    data class Confirm(
        val icon: DeviceIcon,
        val name: UiText,
        val subtitle: UiText,
        val caution: Boolean,
        val scopes: List<ScopeSwitch>,
        val note: UiText,
        val primary: UiText,
        val renewal: Boolean = false,
        val busy: Boolean = false,
        val grants: List<GrantGroup> = emptyList(),
        val ask: ConfirmAsk? = null,
        val ready: Boolean = true,
    ) : PairingUiState {
        val canProceed: Boolean get() = !busy && ready && (scopes.isEmpty() || scopes.any { it.checked })
    }

    /**
     * The official ITMO.ID page for the client, full screen, in a browser with its own empty store and [userAgent].
     * Every main-frame navigation goes through [PairingViewModel.isSignInCallback]; the callback is never loaded and
     * goes to [PairingViewModel.onSignInCallback], with the store's `KEYCLOAK_IDENTITY` when [readIdentity].
     */
    data class SigningIn(
        val url: String,
        val userAgent: String,
        val title: UiText,
        val readIdentity: Boolean = false,
    ) : PairingUiState

    data object Sending : PairingUiState

    data class Result(
        val success: Boolean,
        val title: UiText,
        val text: UiText,
        val action: UiText,
        val next: ResultAction,
    ) : PairingUiState
}

/** A group of grant switches on the card: «Виджеты» or «ИТМО». */
data class GrantGroup(val title: UiText, val grants: List<GrantSwitch>)

/**
 * One requested grant as a switch: [description] says what it gives, [purpose] what this client does with it
 * («Зачем: …»), [warning] is the high-risk warning under `itmo:sso`, [note] is «Нужно для работы» (on and locked) or
 * «Недоступно в этой версии» (an ITMO grant this version cannot mint: off and locked). [enabled] is false for a locked
 * switch; the card also locks every switch while it is busy.
 */
data class GrantSwitch(
    val id: String,
    val title: UiText,
    val description: UiText?,
    val purpose: UiText?,
    val warning: UiText?,
    val note: UiText?,
    val checked: Boolean,
    val enabled: Boolean,
)

/** What the result's button does: close the sheet, sign in again, or scan a new link. */
enum class ResultAction { CLOSE, SIGN_IN_AGAIN, SCAN_AGAIN }
