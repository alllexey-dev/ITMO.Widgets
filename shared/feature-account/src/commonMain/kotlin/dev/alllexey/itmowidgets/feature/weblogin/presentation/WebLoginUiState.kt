package dev.alllexey.itmowidgets.feature.weblogin.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind

sealed interface WebLoginUiState {
    /** Typing or scanning; [error] says why the last code was not taken. */
    data class Input(val code: String = "", val error: UiText? = null) : WebLoginUiState {
        val canSubmit: Boolean get() = code.isNotBlank()
    }

    data class Checking(val code: String) : WebLoginUiState

    /**
     * The device behind a code or link, waiting for [approve]. [name] is the device's own choice, [client] its
     * browser or version; [scopes] are switches for a new agent ([scopesEditable]) and the existing permissions of
     * an agent session. [sharedSession] and [verification] are shown for devices that receive the ITMO session.
     */
    data class Confirm(
        val code: String,
        val kind: PairingKind,
        val title: UiText,
        val name: UiText?,
        val client: UiText?,
        val requestedAt: UiText,
        val access: UiText,
        val scopes: List<ScopeChoice>,
        val scopesEditable: Boolean,
        val sharedSession: Boolean,
        val verification: UiText?,
        val warning: UiText,
        val approve: UiText,
        val approving: Boolean = false,
    ) : WebLoginUiState {
        val canApprove: Boolean get() = !approving && (!scopesEditable || scopes.any { it.checked })
    }

    /** The screen lock asks the owner. */
    data object Unlocking : WebLoginUiState

    /** The phone takes a new ITMO session and hands out the previous one. */
    data object Rotating : WebLoginUiState

    /** The sealed session goes to Backend. */
    data object Sending : WebLoginUiState

    data class Done(val text: UiText) : WebLoginUiState

    /** [code] is what a retry checks again; empty when the code itself is gone. */
    data class Error(val text: UiText, val code: String) : WebLoginUiState
}

data class ScopeChoice(val scope: AgentScope, val title: UiText, val checked: Boolean)
