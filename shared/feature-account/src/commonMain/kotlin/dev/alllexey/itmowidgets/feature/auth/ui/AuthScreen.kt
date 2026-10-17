package dev.alllexey.itmowidgets.feature.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoLoadingIndicator
import dev.alllexey.itmowidgets.designsystem.components.expressive.ItmoMarkTile
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.auth.presentation.AuthUiState
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.compose.strings.app_name
import dev.alllexey.itmowidgets.shared.compose.strings.app_unofficial_notice
import dev.alllexey.itmowidgets.shared.compose.strings.auth_initializing
import dev.alllexey.itmowidgets.shared.compose.strings.auth_signing_in
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_paste
import dev.alllexey.itmowidgets.shared.designsystem.ic_key
import dev.alllexey.itmowidgets.shared.designsystem.ic_lock
import dev.alllexey.itmowidgets.shared.designsystem.ic_login
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_login_itmo_id
import dev.alllexey.itmowidgets.shared.feature.account.auth_login_refresh_token
import dev.alllexey.itmowidgets.shared.feature.account.auth_logo
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_hint
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_note
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_placeholder
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_required
import dev.alllexey.itmowidgets.shared.feature.account.auth_manual_token_sign_in
import dev.alllexey.itmowidgets.shared.feature.account.auth_official_page_notice
import dev.alllexey.itmowidgets.shared.feature.account.auth_other_text
import dev.alllexey.itmowidgets.shared.feature.account.auth_reauthentication_description
import dev.alllexey.itmowidgets.shared.feature.account.auth_tagline
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sign-in screen (`auth`, DECISIONS.md «Вход»): the app mark, the name, one line on what the app does, and one
 * main path, `Войти через ITMO.ID`, with the note that the password goes only to the ITMO.ID page. The refresh token
 * waits under `Другие способы входа` in a sheet. While the session is checked only the loading indicator shows.
 * The mark is the hidden demo entry (ADR 0015): every tap goes to [onLogoTap] through a plain pointer handler, not a
 * click, so accessibility services never stop on it, and nothing else on the screen names the demo.
 * [onSignInWithToken] gets the token typed into the sheet; the token lives in the sheet's composition only and is
 * dropped when the sheet closes.
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
        OtherWaysSheet(
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
    val colors = ItmoTheme.colorScheme
    val textModifier = Modifier.fillMaxWidth()
    Column(
        Modifier
            .fillMaxSize()
            .testTag(AuthTestTags.CONTENT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = ItmoTheme.spacing.group),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.heightIn(min = TopSpace))
        Logo(onLogoTap)
        Text(
            stringResource(CoreRes.string.app_name),
            textModifier.padding(top = ItmoTheme.spacing.group),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            style = ItmoTheme.emphasizedTypography.displaySmall,
        )
        Text(
            stringResource(
                if (state.reauthenticationRequired) Res.string.auth_reauthentication_description else Res.string.auth_tagline,
            ),
            textModifier
                .padding(top = ItmoTheme.spacing.compact)
                .then(if (state.reauthenticationRequired) Modifier.testTag(AuthTestTags.REAUTH_NOTICE) else Modifier),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = ItmoTheme.typography.bodyLarge,
        )
        Spacer(Modifier.heightIn(min = ButtonsTopSpace))
        state.error?.let { error ->
            Text(
                error.asString(),
                textModifier
                    .padding(bottom = ItmoTheme.spacing.content)
                    .testTag(AuthTestTags.ERROR),
                color = colors.error,
                textAlign = TextAlign.Center,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        ProgressButton(
            label = stringResource(Res.string.auth_login_itmo_id),
            onClick = onSignInWithItmoId,
            modifier = textModifier
                .heightIn(min = MainButtonHeight)
                .testTag(AuthTestTags.ITMO_ID_LOGIN),
            enabled = loginEnabled,
            inProgress = state.sessionTransitionInProgress,
            icon = painterResource(KitRes.drawable.ic_login),
        )
        Row(
            Modifier.padding(top = ItmoTheme.spacing.content),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        ) {
            Icon(painterResource(KitRes.drawable.ic_lock), null, Modifier.size(NoteIconSize), tint = colors.onSurfaceVariant)
            Text(
                stringResource(Res.string.auth_official_page_notice),
                color = colors.onSurfaceVariant,
                style = ItmoTheme.typography.bodySmall,
            )
        }
        ProgressButton(
            label = stringResource(Res.string.auth_login_refresh_token),
            onClick = onAskToken,
            modifier = Modifier
                .padding(top = ItmoTheme.spacing.compact)
                .testTag(AuthTestTags.REFRESH_TOKEN_LOGIN),
            style = ProgressButtonStyle.Text,
            enabled = loginEnabled,
        )
        if (state.manualLoginInProgress) {
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
            stringResource(CoreRes.string.app_unofficial_notice),
            textModifier
                .padding(top = ItmoTheme.spacing.compact)
                .testTag(AuthTestTags.UNOFFICIAL_NOTICE),
            color = colors.outline,
            textAlign = TextAlign.Center,
            style = ItmoTheme.typography.labelSmall,
        )
    }
}

/** The app mark in its cookie tile, decorative for accessibility services; taps reach [onTap] without a click action. */
@Composable
private fun Logo(onTap: () -> Unit) {
    val currentOnTap by rememberUpdatedState(onTap)
    ItmoMarkTile(
        LogoTileSize,
        Modifier
            .testTag(AuthTestTags.LOGO)
            .pointerInput(Unit) { detectTapGestures(onTap = { currentOnTap() }) },
    ) {
        Image(painterResource(Res.drawable.auth_logo), contentDescription = null, modifier = Modifier.size(LogoSize))
    }
}

/** `Другие способы входа`: the refresh token in a sheet over the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtherWaysSheet(onSignIn: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = ItmoTheme.colorScheme.surfaceContainerLow,
    ) {
        OtherWaysSheetContent(onSignIn, onDismiss)
    }
}

/**
 * The sheet's body: why it exists, then the refresh token card. `Войти по токену` with an empty field shows
 * `auth_manual_token_required` and keeps the sheet; otherwise the field is emptied and [onSignIn] gets the token.
 * The token is `remember`ed, never saved into the instance state, so it leaves with the sheet.
 */
@Composable
internal fun OtherWaysSheetContent(onSignIn: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var token by remember { mutableStateOf("") }
    var missing by remember { mutableStateOf(false) }
    val colors = ItmoTheme.colorScheme
    SheetScaffold(
        title = stringResource(Res.string.auth_login_refresh_token),
        modifier = modifier.testTag(AuthTestTags.OTHER_WAYS),
        close = SheetClose(stringResource(CoreRes.string.common_close)) {
            token = ""
            onDismiss()
        },
    ) {
        Column(
            Modifier.padding(horizontal = ItmoTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Text(stringResource(Res.string.auth_other_text), color = colors.onSurfaceVariant, style = ItmoTheme.typography.bodyLarge)
            ItmoCard {
                Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(painterResource(KitRes.drawable.ic_key), Tone.Primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.auth_manual_token_hint), style = ItmoTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.auth_manual_token_note),
                            color = colors.onSurfaceVariant,
                            style = ItmoTheme.typography.bodyMedium,
                        )
                    }
                }
                RefreshTokenField(token, missing, onValueChange = {
                    token = it
                    missing = false
                })
                ProgressButton(
                    label = stringResource(Res.string.auth_manual_token_sign_in),
                    onClick = {
                        if (token.isBlank()) {
                            missing = true
                        } else {
                            val value = token
                            token = ""
                            onSignIn(value)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(AuthTestTags.TOKEN_SIGN_IN),
                )
            }
        }
    }
}

/** An outlined password field, one line, no suggestions, with the paste mark. */
@Composable
private fun RefreshTokenField(token: String, missing: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = token,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(AuthTestTags.TOKEN_INPUT),
        placeholder = { Text(stringResource(Res.string.auth_manual_token_placeholder)) },
        trailingIcon = { Icon(painterResource(KitRes.drawable.ic_content_paste), contentDescription = null) },
        supportingText = if (missing) {
            { Text(stringResource(Res.string.auth_manual_token_required)) }
        } else {
            null
        },
        isError = missing,
        shape = ItmoTheme.shapes.large,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        singleLine = true,
    )
}

/** Tags for host and instrumented tests. */
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
    const val OTHER_WAYS = "auth_other_ways"
    const val TOKEN_INPUT = "refresh_token_input"
    const val TOKEN_SIGN_IN = "refresh_token_sign_in"
}

private val LogoTileSize = 132.dp
private val LogoSize = 96.dp
private val ScreenPadding = 24.dp
private val TopSpace = 96.dp
private val ButtonsTopSpace = 72.dp
private val MainButtonHeight = 56.dp
private val NoteIconSize = 16.dp
/** The kit's loading indicator for a short wait. */
private val ManualProgressSize = 32.dp
