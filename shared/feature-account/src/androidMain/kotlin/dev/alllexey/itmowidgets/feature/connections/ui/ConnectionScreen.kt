package dev.alllexey.itmowidgets.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.presentation.ConnectionUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.ConnectionViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.RenewSheet
import dev.alllexey.itmowidgets.feature.connections.presentation.SignInStatus
import dev.alllexey.itmowidgets.feature.connections.presentation.SignInTone
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_content_copy
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_key
import dev.alllexey.itmowidgets.shared.designsystem.ic_logout
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code_scanner
import dev.alllexey.itmowidgets.shared.designsystem.ic_refresh
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/** What a client's screen asks of its host besides the ViewModel's calls. */
@Immutable
class ConnectionActions(
    val onBack: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onScope: (String, Boolean) -> Unit = { _, _ -> },
    val onRenew: () -> Unit = {},
    val onCloseRenew: () -> Unit = {},
    val onRevoke: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun ConnectionRoute(
    viewModel: ConnectionViewModel,
    unlock: DeviceUnlock,
    onBack: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, unlock, onBack, onScan) {
        ConnectionActions(
            onBack = onBack,
            onScan = {
                viewModel.closeRenew()
                onScan()
            },
            onScope = { id, checked -> viewModel.onScopeChanged(id, checked, unlock) },
            onRenew = viewModel::askRenew,
            onCloseRenew = viewModel::closeRenew,
            onRevoke = viewModel::askRevoke,
            onConfirm = viewModel::revoke,
            onDismissConfirm = viewModel::dismissConfirm,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    ConnectionScreen(state, actions, modifier)
}

/** A connected client (UX spec section 4): its ITMO sign-in, access, facts and «Отключить». */
@Composable
fun ConnectionScreen(state: ConnectionUiState, actions: ConnectionActions, modifier: Modifier = Modifier) {
    val snackbars = remember { SnackbarHostState() }
    ItmoDetailScreen(
        title = state.title.asString(),
        subtitle = state.subtitle?.asString(),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                state.gone -> ContentState(
                    title = text(AccountStrings.connection_gone),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    icon = painterResource(Res.drawable.ic_error),
                )
                state.loading -> ScreenColumn(padding) { Skeleton(SkeletonStyle.List, rows = 4) }
                else -> ScreenColumn(padding) { ConnectionContent(state, actions) }
            }
            ItmoSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            actions.onMessageShown()
        }
    }
    state.renew?.let { renew -> ConnectionsSheet(actions.onCloseRenew) { RenewContent(renew, state.title.asString(), actions) } }
    state.confirm?.let { ConfirmAskDialog(it, actions.onConfirm, actions.onDismissConfirm) }
}

@Composable
private fun ConnectionContent(state: ConnectionUiState, actions: ConnectionActions) {
    state.signIn?.let { signIn ->
        SectionHeader(text(AccountStrings.connection_section_sign_in))
        SignIn(signIn, actions)
    }
    if (state.scopes.isNotEmpty()) {
        SectionHeader(text(AccountStrings.connection_section_access))
        ListGroup(Modifier.testTag(ConnectionTestTags.SCOPES)) {
            state.scopes.forEach { scope ->
                item {
                    SwitchListRow(
                        scope.title.asString(),
                        checked = scope.checked,
                        onCheckedChange = { checked -> scope.id?.let { actions.onScope(it, checked) } },
                        modifier = Modifier.testTag(ConnectionTestTags.scope(scope.id.orEmpty())),
                        subtitle = scope.subtitle.asString(),
                        enabled = scope.id != null && !state.busy,
                    )
                }
            }
        }
    }
    SectionHeader(text(AccountStrings.connection_section_about))
    FactsGroup(state.facts)
    ListGroup {
        item {
            ListRow(
                state.revoke.asString(),
                Modifier.testTag(ConnectionTestTags.REVOKE),
                icon = painterResource(Res.drawable.ic_logout),
                iconTone = Tone.Neutral,
                danger = true,
                onClick = actions.onRevoke,
            )
        }
    }
}

@Composable
private fun SignIn(signIn: SignInStatus, actions: ConnectionActions) = ListGroup {
    item {
        ListRow(
            signIn.title.asString(),
            subtitle = signIn.text?.asString(),
            icon = painterResource(if (signIn.tone == SignInTone.ERROR) Res.drawable.ic_error else Res.drawable.ic_key),
            iconTone = when (signIn.tone) {
                SignInTone.OK -> Tone.Neutral
                SignInTone.WARNING -> Tone.Warning
                SignInTone.ERROR -> Tone.Error
            },
        )
    }
    signIn.action?.let { action ->
        item {
            ListRow(
                action.asString(),
                Modifier.testTag(ConnectionTestTags.RENEW),
                icon = painterResource(Res.drawable.ic_refresh),
                iconTone = Tone.Primary,
                chevron = true,
                onClick = actions.onRenew,
            )
        }
    }
}

@Composable
internal fun RenewContent(renew: RenewSheet, name: String, actions: ConnectionActions) {
    val clipboard = LocalClipboardManager.current
    SheetScaffold(
        title = renew.title.asString(),
        close = SheetClose(text(CoreStrings.common_close), actions.onCloseRenew),
        footer = {
            ProgressButton(
                text(AccountStrings.connection_renew_scan),
                onClick = actions.onScan,
                modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).fillMaxWidth(),
                icon = painterResource(Res.drawable.ic_qr_code_scanner),
            )
        },
    ) {
        Column(
            Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            Text(
                text(AccountStrings.connection_renew_lead, name),
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.bodyLarge,
            )
            ItmoCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        renew.command,
                        Modifier.weight(1f),
                        style = ItmoTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(renew.command)) }) {
                        Icon(
                            painterResource(Res.drawable.ic_content_copy),
                            text(AccountStrings.connection_copy),
                            tint = ItmoTheme.colorScheme.primary,
                        )
                    }
                }
            }
            ListGroupNote(renew.note.asString())
        }
    }
}

/** Test tags of [ConnectionScreen]. */
object ConnectionTestTags {
    const val SCOPES = "connection_scopes"
    const val RENEW = "connection_renew"
    const val REVOKE = "connection_revoke"

    fun scope(id: String): String = "connection_scope:$id"
}
