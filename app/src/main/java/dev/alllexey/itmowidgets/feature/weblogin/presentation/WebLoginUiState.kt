package dev.alllexey.itmowidgets.feature.weblogin.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview

sealed interface WebLoginUiState {
    /** Typing or scanning; [error] says why the last code was not taken. */
    data class Input(val code: String = "", val error: UiText? = null) : WebLoginUiState {
        val canSubmit: Boolean get() = code.isNotBlank()
    }

    data class Checking(val code: String) : WebLoginUiState

    /** The browser behind the code, waiting for «Войти». */
    data class Confirm(
        val code: String,
        val preview: WebLoginPreview,
        val browser: UiText,
        val requestedAt: UiText,
        val approving: Boolean = false,
    ) : WebLoginUiState

    data object Done : WebLoginUiState

    /** [code] is what a retry checks again; empty when the code itself is gone. */
    data class Error(val text: UiText, val code: String) : WebLoginUiState
}
