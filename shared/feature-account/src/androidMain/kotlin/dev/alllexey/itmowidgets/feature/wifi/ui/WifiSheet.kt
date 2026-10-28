package dev.alllexey.itmowidgets.feature.wifi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateActionStyle
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateLoading
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiStatus
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiUiState
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import dev.alllexey.itmowidgets.shared.designsystem.ic_visibility
import dev.alllexey.itmowidgets.shared.designsystem.ic_visibility_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_wifi
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/** What the Wi-Fi sheet asks its route to do; copying and connecting are platform work. */
data class WifiActions(
    val onClose: () -> Unit = {},
    val onConnect: (WifiCredentials) -> Unit = {},
    val onCopyLogin: (String) -> Unit = {},
    val onCopyPassword: (String) -> Unit = {},
    val onTogglePassword: () -> Unit = {},
    /** ISU's Wi-Fi page in the browser. */
    val onOpenIsu: () -> Unit = {},
    /** The ITMO.ID sign-in that leaves the phone the session ISU needs. */
    val onSignIn: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Test tags of [WifiSheetContent]. */
object WifiTestTags {
    const val LOGIN = "wifi_login"
    const val PASSWORD = "wifi_password"
    const val TOGGLE = "wifi_password_toggle"
    const val CONNECT = "wifi_connect"
    const val STATE = "wifi_state"
    const val SIGN_IN = "wifi_sign_in"
}

/**
 * The Wi-Fi sheet: the network, the login and the password (dots until shown) with copy buttons, the manual settings
 * as a note, and `Подключиться` at the bottom; without credentials the state of reading ISU instead: no password yet
 * (opens ISU's page), no ITMO.ID session (`Войти через ITMO.ID`), an error with a retry, or loading. Stateless; [WifiRoute] feeds it.
 */
@Composable
fun WifiSheetContent(
    state: WifiUiState,
    actions: WifiActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val credentials = state.credentials
    Box(modifier) {
        SheetScaffold(
            title = text(AccountStrings.wifi_title),
            close = SheetClose(text(CoreStrings.common_close), actions.onClose),
            footer = credentials?.let {
                {
                    ProgressButton(
                        text(AccountStrings.wifi_connect),
                        onClick = { actions.onConnect(it) },
                        modifier = Modifier
                            .padding(horizontal = ItmoTheme.spacing.screenMargin)
                            .fillMaxWidth()
                            .testTag(WifiTestTags.CONNECT),
                        icon = painterResource(Res.drawable.ic_wifi),
                    )
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ItmoTheme.spacing.screenMargin)
                    .padding(bottom = ItmoTheme.spacing.compact),
                verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
            ) {
                if (credentials != null) Credentials(state, credentials, actions) else Status(state.status, actions)
            }
        }
        ItmoSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Credentials(state: WifiUiState, credentials: WifiCredentials, actions: WifiActions) {
    val copyLogin = text(AccountStrings.wifi_copy_login)
    val copyPassword = text(AccountStrings.wifi_copy_password)
    ListGroup {
        item { Field(text(AccountStrings.wifi_network), state.network) }
        item {
            Field(text(AccountStrings.wifi_login), credentials.login, Modifier.testTag(WifiTestTags.LOGIN)) {
                FieldButton(painterResource(Res.drawable.ic_content_copy), copyLogin) {
                    actions.onCopyLogin(credentials.login)
                }
            }
        }
        item {
            val shown = state.passwordShown
            Field(
                text(AccountStrings.wifi_password),
                if (shown) credentials.password else HIDDEN,
                Modifier.testTag(WifiTestTags.PASSWORD),
                monospace = shown,
                hidden = !shown,
            ) {
                FieldButton(
                    painterResource(if (shown) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility),
                    text(if (shown) AccountStrings.wifi_password_hide else AccountStrings.wifi_password_show),
                    Modifier.testTag(WifiTestTags.TOGGLE),
                    actions.onTogglePassword,
                )
                FieldButton(painterResource(Res.drawable.ic_content_copy), copyPassword) {
                    actions.onCopyPassword(credentials.password)
                }
            }
        }
    }
    ListGroupNote(text(AccountStrings.wifi_manual, state.serverDomain))
}

/** A label over its value, with the value's buttons at the end; a [hidden] value is not read out. */
@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    hidden: Boolean = false,
    buttons: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = ItmoTheme.spacing.cardPadding, end = ItmoTheme.spacing.compact)
            .padding(vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = ItmoTheme.spacing.compact)) {
            Text(label, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
            Text(
                value,
                if (hidden) Modifier.clearAndSetSemantics { } else Modifier,
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            )
        }
        buttons()
    }
}

@Composable
private fun FieldButton(icon: Painter, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, label, tint = ItmoTheme.colorScheme.primary)
    }
}

@Composable
private fun Status(status: WifiStatus, actions: WifiActions) {
    val modifier = Modifier.testTag(WifiTestTags.STATE)
    val openIsu = ContentStateAction(text(AccountStrings.wifi_open_isu), actions.onOpenIsu)
    when (status) {
        WifiStatus.NoPassword -> ContentState(
            text(AccountStrings.wifi_no_password_title),
            modifier,
            ContentStateSize.Compact,
            painterResource(Res.drawable.ic_wifi),
            text(AccountStrings.wifi_no_password_description),
            openIsu,
        )
        WifiStatus.SignInRequired -> ContentState(
            text(AccountStrings.wifi_sign_in_title),
            modifier,
            ContentStateSize.Compact,
            painterResource(Res.drawable.ic_wifi),
            text(AccountStrings.wifi_sign_in_description),
            ContentStateAction(
                text(CoreStrings.auth_login_itmo_id),
                actions.onSignIn,
                ContentStateActionStyle.Filled,
                Modifier.testTag(WifiTestTags.SIGN_IN),
            ),
        )
        is WifiStatus.Failed -> ContentState(
            text(CoreStrings.common_load_error_title),
            modifier,
            ContentStateSize.Compact,
            description = status.error.toUiText().asString(),
            action = ContentStateAction(text(CoreStrings.common_retry), actions.onRetry),
        )
        WifiStatus.Loading, is WifiStatus.Ready -> ContentStateLoading(modifier, ContentStateSize.Compact)
    }
}

@Composable
private fun text(key: String, vararg arguments: Any): String = UiText.Res(key, arguments.toList()).asString()

/** The same eight dots for every password, so the hidden field does not tell its length. */
private const val HIDDEN = "••••••••"
