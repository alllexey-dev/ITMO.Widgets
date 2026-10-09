package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthUiState
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.app_name
import dev.alllexey.itmowidgets.shared.core.app_unofficial_notice
import dev.alllexey.itmowidgets.shared.core.auth_initializing
import dev.alllexey.itmowidgets.shared.core.auth_signing_in
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_exercise
import dev.alllexey.itmowidgets.shared.designsystem.ic_group
import dev.alllexey.itmowidgets.shared.designsystem.ic_login
import dev.alllexey.itmowidgets.shared.designsystem.ic_schedule
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_feature_friends
import dev.alllexey.itmowidgets.shared.feature.account.auth_feature_sport
import dev.alllexey.itmowidgets.shared.feature.account.auth_feature_widgets
import dev.alllexey.itmowidgets.shared.feature.account.auth_login_itmo_id
import dev.alllexey.itmowidgets.shared.feature.account.auth_login_refresh_token
import dev.alllexey.itmowidgets.shared.feature.account.auth_logo
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_dialog_description
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_dialog_title
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_hint
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_required
import dev.alllexey.itmowidgets.shared.feature.account.auth_official_page_notice
import dev.alllexey.itmowidgets.shared.feature.account.auth_reauthentication_description
import dev.alllexey.itmowidgets.shared.feature.account.auth_sign_in
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sign-in screen (`auth`): the logo, the app name, what the app does, the ITMO.ID sign-in and the refresh-token
 * sign-in, with the notices under them. While the session is checked only the loading indicator shows. The logo is the hidden
 * demo entry: every tap goes to [onLogoTap] through a plain pointer handler, not a click, so accessibility services
 * never stop on it. [onSignInWithToken] gets the token typed into the refresh-token dialog; the token lives in that
 * dialog's composition only and is dropped when the dialog closes.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onLogoTap: () -> Unit,
    onSignInWithItmoId: () -> Unit,
    onSignInWithToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asksToken by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        if (state.initializing) {
            val description = stringResource(CoreRes.string.auth_initializing)
            ItmoLoadingIndicator(
                Modifier
                    .align(Alignment.Center)
                    .testTag(AuthTestTags.PROGRESS)
                    .semantics { contentDescription = description },
            )
        } else {
            AuthContent(state, onLogoTap, onSignInWithItmoId, onAskToken = { asksToken = true })
        }
    }
    if (asksToken) {
        RefreshTokenDialog(
            onSignIn = { token ->
                asksToken = false
                onSignInWithToken(token)
            },
            onDismiss = { asksToken = false },
        )
    }
}

@Composable
private fun AuthContent(
    state: AuthUiState,
    onLogoTap: () -> Unit,
    onSignInWithItmoId: () -> Unit,
    onAskToken: () -> Unit,
) {
    val loginEnabled = !state.manualLoginInProgress && !state.sessionTransitionInProgress
    val textModifier = Modifier.fillMaxWidth()
    Column(
        Modifier
            .fillMaxSize()
            .testTag(AuthTestTags.CONTENT)
            .verticalScroll(rememberScrollState())
            .padding(start = ScreenPadding, top = TopPadding, end = ScreenPadding, bottom = BottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(onLogoTap)
        Text(
            stringResource(CoreRes.string.app_name),
            textModifier.padding(top = ItmoTheme.spacing.section),
            color = ItmoTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = ItmoTheme.typography.headlineSmall,
        )
        // The feature lines stay; only an expired session needs a line explaining itself.
        if (state.reauthenticationRequired) {
            Text(
                stringResource(Res.string.auth_reauthentication_description),
                textModifier
                    .padding(top = ItmoTheme.spacing.compact)
                    .testTag(AuthTestTags.REAUTH_NOTICE),
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = ItmoTheme.typography.bodyLarge,
            )
        }
        Column(
            Modifier.padding(top = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Features.forEach { (icon, text) -> FeatureRow(icon, text) }
        }
        state.error?.let { error ->
            Text(
                error.asString(),
                textModifier
                    .padding(top = ItmoTheme.spacing.group)
                    .testTag(AuthTestTags.ERROR),
                color = ItmoTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        ProgressButton(
            label = stringResource(Res.string.auth_login_itmo_id),
            onClick = onSignInWithItmoId,
            modifier = textModifier
                .padding(top = ButtonsTopPadding)
                .testTag(AuthTestTags.ITMO_ID_LOGIN),
            enabled = loginEnabled,
            icon = painterResource(KitRes.drawable.ic_login),
        )
        ProgressButton(
            label = stringResource(Res.string.auth_login_refresh_token),
            onClick = onAskToken,
            modifier = Modifier
                .padding(top = ItmoTheme.spacing.compact)
                .testTag(AuthTestTags.REFRESH_TOKEN_LOGIN),
            style = ProgressButtonStyle.Text,
            enabled = loginEnabled,
        )
        if (state.manualLoginInProgress || state.sessionTransitionInProgress) {
            val description = stringResource(CoreRes.string.auth_signing_in)
            ItmoLoadingIndicator(
                Modifier
                    .padding(top = ItmoTheme.spacing.compact)
                    .size(ManualProgressSize)
                    .testTag(AuthTestTags.MANUAL_PROGRESS)
                    .semantics { contentDescription = description },
            )
        }
        Text(
            stringResource(Res.string.auth_official_page_notice),
            textModifier.padding(top = ItmoTheme.spacing.section),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = ItmoTheme.typography.bodySmall,
        )
        Text(
            stringResource(CoreRes.string.app_unofficial_notice),
            textModifier
                .padding(top = ItmoTheme.spacing.compact)
                .testTag(AuthTestTags.UNOFFICIAL_NOTICE),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

/** The 88 dp logo, decorative for accessibility services; taps reach [onTap] without a click action. */
@Composable
private fun Logo(onTap: () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    Image(
        painterResource(Res.drawable.auth_logo),
        contentDescription = null,
        modifier = Modifier
            .size(LogoSize)
            .testTag(AuthTestTags.LOGO)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnTap() }) },
    )
}

/** `Widget.ItmoWidgets.FeatureRow`: a 24 dp `onSurfaceVariant` icon, 12 dp, the text in `bodyLarge`. */
@Composable
private fun FeatureRow(icon: DrawableResource, text: StringResource) {
    Row(Modifier.heightIn(min = FeatureIconSize), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(end = ItmoTheme.spacing.content)
                .size(FeatureIconSize),
            tint = ItmoTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(text), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
    }
}

/**
 * Asks for a refresh token. «Войти» with an empty field shows `auth_manual_token_required` and keeps the dialog;
 * otherwise the field is emptied and [onSignIn] gets the token. The token is `remember`ed, never saved into the
 * instance state, so it leaves with the dialog.
 */
@Composable
private fun RefreshTokenDialog(onSignIn: (String) -> Unit, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    ConfirmDialog(
        title = stringResource(Res.string.auth_manual_dialog_title),
        confirmLabel = stringResource(Res.string.auth_sign_in),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onConfirm = {
            if (token.isBlank()) {
                missing = true
            } else {
                val value = token
                token = ""
                onSignIn(value)
            }
        },
        onDismiss = {
            token = ""
            onDismiss()
        },
        text = stringResource(Res.string.auth_manual_dialog_description),
        content = { RefreshTokenField(token, missing, onValueChange = { token = it }) },
    )
}

/** The refresh-token dialog without its window, for previews. */
@Composable
internal fun RefreshTokenDialogSurface(token: String, missing: Boolean, modifier: Modifier = Modifier) {
    ConfirmDialogSurface(
        title = stringResource(Res.string.auth_manual_dialog_title),
        confirmLabel = stringResource(Res.string.auth_sign_in),
        dismissLabel = stringResource(CoreRes.string.common_cancel),
        onConfirm = {},
        onDismiss = {},
        modifier = modifier,
        text = stringResource(Res.string.auth_manual_dialog_description),
        content = { RefreshTokenField(token, missing, onValueChange = {}) },
    )
}

/** `dialog_auth_refresh_token.xml`: an outlined password field, one line, no suggestions. */
@Composable
private fun RefreshTokenField(token: String, missing: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = token,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AuthTestTags.TOKEN_INPUT),
        label = { Text(stringResource(Res.string.auth_manual_token_hint)) },
        supportingText = if (missing) {
            { Text(stringResource(Res.string.auth_manual_token_required)) }
        } else {
            null
        },
        isError = missing,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        singleLine = true,
    )
}

/** Tags named after the View ids of `fragment_auth.xml`, for host and instrumented tests. */
object AuthTestTags {
    const val PROGRESS = "auth_progress"
    const val CONTENT = "auth_content"
    const val LOGO = "auth_logo"
    const val REAUTH_NOTICE = "auth_reauth_notice"
    const val ERROR = "auth_error"
    const val ITMO_ID_LOGIN = "itmo_id_login_button"
    const val REFRESH_TOKEN_LOGIN = "refresh_token_login_button"
    const val MANUAL_PROGRESS = "manual_login_progress"
    const val UNOFFICIAL_NOTICE = "auth_unofficial_notice"
    const val TOKEN_INPUT = "refresh_token_input"
}

private val Features = listOf(
    KitRes.drawable.ic_schedule to Res.string.auth_feature_widgets,
    KitRes.drawable.ic_exercise to Res.string.auth_feature_sport,
    KitRes.drawable.ic_group to Res.string.auth_feature_friends,
)

private val LogoSize = 88.dp
private val FeatureIconSize = 24.dp
private val ScreenPadding = 24.dp
private val TopPadding = 64.dp
private val BottomPadding = 32.dp
private val ButtonsTopPadding = 32.dp
/** The XML `manual_login_progress`'s 32 dp, now the kit's loading indicator for a short wait. */
private val ManualProgressSize = 32.dp
