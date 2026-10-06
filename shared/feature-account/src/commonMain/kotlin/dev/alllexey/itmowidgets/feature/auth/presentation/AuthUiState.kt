package dev.alllexey.itmowidgets.feature.auth.presentation

import dev.alllexey.itmowidgets.core.text.UiText

data class AuthUiState(
    val initializing: Boolean = true,
    val sessionTransitionInProgress: Boolean = false,
    val reauthenticationRequired: Boolean = false,
    val manualLoginInProgress: Boolean = false,
    val error: UiText? = null
)

sealed interface AuthEvent {
    data object DemoStarted : AuthEvent
}
