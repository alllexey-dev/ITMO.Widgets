package dev.alllexey.itmowidgets.feature.auth.presentation

import dev.alllexey.itmowidgets.core.text.UiText

/** The ITMO.ID page inside the sign-in browser. */
enum class LoginPage {
    Loading,
    Shown,

    /** The main frame failed or left the allowed pages; it stays failed until [InteractiveLoginViewModel.retry]. */
    Failed
}

data class InteractiveLoginUiState(
    val page: LoginPage = LoginPage.Loading,
    val completingLogin: Boolean = false,
    /** A failed sign-in; like a failed page it replaces the browser until a retry. */
    val error: UiText? = null
) {
    val showsError: Boolean get() = page == LoginPage.Failed || error != null
}

sealed interface InteractiveLoginEvent {
    data object Completed : InteractiveLoginEvent
}
