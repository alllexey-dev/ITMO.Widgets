package dev.alllexey.itmowidgets.feature.weblogin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicatorSize
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.StatusPill
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionRow
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.common_back
import dev.alllexey.itmowidgets.shared.compose.strings.common_cancel
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.compose.strings.connections_empty
import dev.alllexey.itmowidgets.shared.compose.strings.connections_itmo_badge
import dev.alllexey.itmowidgets.shared.compose.strings.connections_revoke
import dev.alllexey.itmowidgets.shared.compose.strings.connections_title
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_computer
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** What «Подключённые устройства» asks of its host; the rest are the ViewModel's calls. */
@Immutable
class ConnectionsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRevoke: (ConnectionRow) -> Unit = {},
    val onConfirmRevoke: () -> Unit = {},
    val onDismissRevoke: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun ConnectionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectionsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack) {
        ConnectionsActions(
            onBack = onBack,
            onRetry = viewModel::refresh,
            onRevoke = { viewModel.askRevoke(it.id) },
            onConfirmRevoke = viewModel::revoke,
            onDismissRevoke = viewModel::dismissRevoke,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    ConnectionsScreen(state, actions, modifier)
}

/**
 * The web sessions and agents with access to the account: kind icon, name, when it connected and was last active,
 * the «Общая сессия My ITMO» badge, and «Отключить» behind a confirmation that says what ends at once and what does
 * not. Stateless; [ConnectionsRoute] feeds it.
 */
@Composable
fun ConnectionsScreen(
    state: ConnectionsUiState,
    actions: ConnectionsActions,
    modifier: Modifier = Modifier,
) {
    val snackbars = remember { SnackbarHostState() }
    ItmoDetailScreen(
        title = stringResource(Res.string.connections_title),
        onBack = actions.onBack,
        backLabel = stringResource(Res.string.common_back),
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ConnectionsUiState.Loading -> ItmoActivityIndicator(
                    Modifier.align(Alignment.Center),
                    size = ItmoActivityIndicatorSize.Large,
                )
                is ConnectionsUiState.Error -> ContentState(
                    title = state.text.asString(),
                    modifier = Modifier.fillMaxSize().testTag(ConnectionsTestTags.ERROR),
                    icon = painterResource(KitRes.drawable.ic_error),
                    action = ContentStateAction(stringResource(Res.string.common_retry), actions.onRetry),
                )
                is ConnectionsUiState.Content -> if (state.rows.isEmpty()) {
                    ContentState(
                        title = stringResource(Res.string.connections_empty),
                        modifier = Modifier.fillMaxSize().testTag(ConnectionsTestTags.EMPTY),
                        icon = painterResource(KitRes.drawable.ic_computer),
                    )
                } else {
                    ConnectionList(state, actions)
                }
            }
            ItmoSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
        }
    }
    val content = state as? ConnectionsUiState.Content
    val message = content?.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbars.showSnackbar(message)
            actions.onMessageShown()
        }
    }
    content?.revoke?.let { row -> RevokeDialog(row, actions) }
}

@Composable
private fun ConnectionList(state: ConnectionsUiState.Content, actions: ConnectionsActions) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag(ConnectionsTestTags.LIST)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.group),
    ) {
        ListGroup {
            state.rows.forEach { row -> item { ConnectionRowItem(row, enabled = !state.revoking, actions) } }
        }
    }
}

/** The kind's tile, the name, when it connected and was last active, the shared-session pill, then «Отключить». */
@Composable
private fun ConnectionRowItem(row: ConnectionRow, enabled: Boolean, actions: ConnectionsActions) {
    ListRow(
        row.title.asString(),
        Modifier.testTag(ConnectionsTestTags.row(row.id.toString())),
        subtitle = listOfNotNull(row.connected, row.lastUsed).map { it.asString() }.joinToString("\n"),
        leading = { IconTile(painterResource(connectionIcon(row.kind)), Tone.Primary) },
        below = {
            if (row.itmoSession) StatusPill(stringResource(Res.string.connections_itmo_badge), tone = Tone.Tertiary)
            ProgressButton(
                label = stringResource(Res.string.connections_revoke),
                onClick = { actions.onRevoke(row) },
                modifier = Modifier.testTag(ConnectionsTestTags.revoke(row.id.toString())),
                style = ProgressButtonStyle.Tonal,
                enabled = enabled,
            )
        },
    )
}

/** «Отключить «nas»?» with what ends and what does not; [windowed] false draws it without its window, for previews. */
@Composable
internal fun RevokeDialog(row: ConnectionRow, actions: ConnectionsActions, windowed: Boolean = true) {
    val title = row.revokeTitle.asString()
    val text = row.revokeText.asString()
    val confirm = stringResource(Res.string.connections_revoke)
    val dismiss = stringResource(Res.string.common_cancel)
    if (windowed) {
        ConfirmDialog(
            title, confirm, dismiss, actions.onConfirmRevoke, actions.onDismissRevoke, text = text,
        )
    } else {
        ConfirmDialogSurface(
            title, confirm, dismiss, actions.onConfirmRevoke, actions.onDismissRevoke, text = text,
        )
    }
}

/** A browser for a web session, a client for every other kind. */
private fun connectionIcon(kind: ConnectionKind): DrawableResource =
    if (kind == ConnectionKind.WEB) KitRes.drawable.ic_computer else KitRes.drawable.ic_code_blocks

/** Test tags of [ConnectionsScreen]. */
object ConnectionsTestTags {
    const val LIST = "connections_list"
    const val EMPTY = "connections_empty"
    const val ERROR = "connections_error"

    fun row(id: String): String = "connections_row:$id"

    fun revoke(id: String): String = "connections_revoke:$id"
}

