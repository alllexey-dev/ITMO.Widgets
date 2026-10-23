package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/** «Подключить устройство» (UX spec section 5). */
sealed interface PairingUiState {
    /** Scan or paste a link; [error] says why the last one was not taken. */
    data class Input(val text: String = "", val error: UiText? = null) : PairingUiState

    data object Opening : PairingUiState

    /**
     * The client and the code to compare. [scopes] are switches of a new agent or MCP server (narrowing only);
     * [renewal] has no card: the host asks for the screen lock at once ([PairingViewModel.proceed]) and the code
     * shows over the sign-in page.
     */
    data class Confirm(
        val icon: DeviceIcon,
        val name: UiText,
        val subtitle: UiText,
        val code: String,
        val scopes: List<ScopeSwitch>,
        val note: UiText,
        val primary: UiText,
        val renewal: Boolean = false,
        val busy: Boolean = false,
    ) : PairingUiState {
        val canProceed: Boolean get() = !busy && (scopes.isEmpty() || scopes.any { it.checked })
    }

    /**
     * The official ITMO.ID page for the client, full screen, in a browser with its own empty store and [userAgent];
     * every main-frame navigation goes through [PairingViewModel.onSignInNavigation]. [code] is shown on a renewal.
     */
    data class SigningIn(val url: String, val userAgent: String, val title: UiText, val code: String?) : PairingUiState

    data object Sending : PairingUiState

    data class Result(
        val success: Boolean,
        val title: UiText,
        val text: UiText,
        val action: UiText,
        val next: ResultAction,
    ) : PairingUiState
}

/** What the result's button does: close the sheet, sign in again, or scan a new link. */
enum class ResultAction { CLOSE, SIGN_IN_AGAIN, SCAN_AGAIN }
