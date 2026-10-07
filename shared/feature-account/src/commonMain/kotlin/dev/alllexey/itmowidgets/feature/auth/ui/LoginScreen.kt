package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarAction
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginUiState
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.auth_signing_in
import dev.alllexey.itmowidgets.shared.core.auth_web_error
import dev.alllexey.itmowidgets.shared.core.auth_web_title
import dev.alllexey.itmowidgets.shared.core.common_close
import dev.alllexey.itmowidgets.shared.core.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The ITMO.ID sign-in page: the top bar (close and `auth_web_title`) over the platform [browser], where the user types
 * the credentials on ITMO.ID's own pages. [browser] is composed in every state, so the host's view keeps its page; a
 * failed page or sign-in ([InteractiveLoginUiState.showsError]) covers it with the error and «Повторить», and the host
 * hides the view itself (recipe platform-view-slot). While the tokens are handed over a spinner sits over the page.
 * The page's own loading spinner is the host's pull-to-refresh, inside [browser].
 */
@Composable
fun LoginScreen(
    state: InteractiveLoginUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    browser: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        AppTopBar(
            title = stringResource(CoreRes.string.auth_web_title),
            navigation = {
                AppTopBarAction(
                    painterResource(KitRes.drawable.ic_close),
                    stringResource(CoreRes.string.common_close),
                    onClose,
                    Modifier.testTag(LoginTestTags.CLOSE),
                )
            },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            browser(Modifier.fillMaxSize())
            if (state.showsError) {
                LoginError(state, onRetry)
            }
            if (state.completingLogin) {
                val description = stringResource(CoreRes.string.auth_signing_in)
                CircularProgressIndicator(
                    Modifier
                        .align(Alignment.Center)
                        .testTag(LoginTestTags.COMPLETING_PROGRESS)
                        .semantics { contentDescription = description },
                )
            }
        }
    }
}

/** `login_error_container`: the error in `bodyLarge` and a text «Повторить», centred over the hidden page. */
@Composable
private fun LoginError(state: InteractiveLoginUiState, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface)
            // Taps on the empty part of the error stay here instead of reaching the page.
            .pointerInput(Unit) {}
            .testTag(LoginTestTags.ERROR_CONTAINER),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = ErrorHorizontalMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                state.error?.asString() ?: stringResource(CoreRes.string.auth_web_error),
                Modifier.fillMaxWidth(),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = ItmoTheme.typography.bodyLarge,
            )
            ProgressButton(
                label = stringResource(CoreRes.string.common_retry),
                onClick = onRetry,
                modifier = Modifier
                    .padding(top = ItmoTheme.spacing.content)
                    .testTag(LoginTestTags.RETRY),
                style = ProgressButtonStyle.Text,
            )
        }
    }
}

/** Tags named after the View ids of `activity_login.xml`, for host and instrumented tests. */
object LoginTestTags {
    const val CLOSE = "login_close"
    const val ERROR_CONTAINER = "login_error_container"
    const val RETRY = "login_retry_button"
    const val COMPLETING_PROGRESS = "login_completing_progress"
}

/** The XML error container's `layout_marginHorizontal`. */
private val ErrorHorizontalMargin = 32.dp
