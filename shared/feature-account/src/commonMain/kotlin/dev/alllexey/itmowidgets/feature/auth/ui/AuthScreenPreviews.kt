package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthUiState
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginUiState
import dev.alllexey.itmowidgets.feature.auth.presentation.LoginPage
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_invalid_credentials

/*
 * The harness names a baseline `<function>_<@Preview name>`, and LA-1c recorded the XML references as
 * `AuthScreen_<state>` and `LoginScreen_error`. Each state therefore is a function called `AuthScreen` or
 * `LoginScreen` in a holder class of its own.
 */

@Composable
private fun AuthPreview(state: AuthUiState) = ItmoPreview {
    AuthScreen(state, onLogoTap = {}, onSignInWithItmoId = {}, onSignInWithToken = {})
}

private val SignedOut = AuthUiState(initializing = false)

internal class AuthScreenInitialPreview {
    @Preview(name = "initial")
    @Composable
    fun AuthScreen() = AuthPreview(SignedOut)
}

internal class AuthScreenReauthenticationPreview {
    @Preview(name = "reauthentication")
    @Composable
    fun AuthScreen() = AuthPreview(SignedOut.copy(reauthenticationRequired = true))
}

internal class AuthScreenSigningInPreview {
    @Preview(name = "signing-in")
    @Composable
    fun AuthScreen() = AuthPreview(SignedOut.copy(manualLoginInProgress = true))
}

internal class AuthScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun AuthScreen() = AuthPreview(SignedOut.copy(error = UiText.Res(Res.string.auth_error_invalid_credentials)))
}

/** The dialog alone on the screen's background, as the XML reference mirrored the dialog window. */
internal class AuthScreenTokenDialogPreview {
    @Preview(name = "token-dialog")
    @Composable
    fun AuthScreen() = ItmoPreview {
        Box(Modifier.background(ItmoTheme.colorScheme.surface).padding(DialogMargin)) {
            RefreshTokenDialogSurface(token = "", missing = false)
        }
    }
}

internal class LoginScreenErrorPreview {
    @Preview(name = "error")
    @Composable
    fun LoginScreen() = ItmoPreview {
        LoginScreen(
            state = InteractiveLoginUiState(page = LoginPage.Failed),
            onClose = {},
            onRetry = {},
            browser = { modifier -> Box(modifier.background(ItmoTheme.colorScheme.surfaceContainerLow)) },
        )
    }
}

/** The space a dialog window keeps around its surface. */
private val DialogMargin = 24.dp
