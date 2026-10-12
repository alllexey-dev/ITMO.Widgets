package dev.alllexey.itmowidgets.feature.weblogin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.buttons.Pill
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicatorSize
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionRow
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.common_back
import dev.alllexey.itmowidgets.shared.compose.strings.common_cancel
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_computer
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.connections_empty
import dev.alllexey.itmowidgets.shared.feature.account.connections_itmo_badge
import dev.alllexey.itmowidgets.shared.feature.account.connections_revoke
import dev.alllexey.itmowidgets.shared.feature.account.connections_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

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
    Box(modifier.fillMaxSize().background(ItmoTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(Res.string.connections_title),
                navigation = { AppTopBarBack(stringResource(CoreRes.string.common_back), actions.onBack) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    ConnectionsUiState.Loading -> ItmoActivityIndicator(
                        Modifier.align(Alignment.Center),
                        size = ItmoActivityIndicatorSize.Large,
                    )
                    is ConnectionsUiState.Error -> ContentState(
                        title = state.text.asString(),
                        modifier = Modifier.fillMaxSize().testTag(ConnectionsTestTags.ERROR),
                        icon = painterResource(KitRes.drawable.ic_error),
                        action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry),
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
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
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
    LazyColumn(
        Modifier.fillMaxSize().testTag(ConnectionsTestTags.LIST),
        contentPadding = PaddingValues(
            start = ItmoTheme.spacing.screenMargin,
            end = ItmoTheme.spacing.screenMargin,
            top = ItmoTheme.spacing.compact,
            bottom = ItmoTheme.spacing.group,
        ),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        items(state.rows, key = { it.id.toString() }) { row ->
            ConnectionCard(row, enabled = !state.revoking, actions)
        }
    }
}

/** Icon, name, connected and last active, the shared-session badge, then «Отключить». */
@Composable
private fun ConnectionCard(row: ConnectionRow, enabled: Boolean, actions: ConnectionsActions) {
    Surface(
        Modifier.fillMaxWidth().testTag(ConnectionsTestTags.row(row.id.toString())),
        shape = ItmoTheme.shapes.cardContent,
        color = ItmoTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(ItmoTheme.spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(connectionIcon(row.kind)),
                    contentDescription = null,
                    modifier = Modifier.padding(end = ItmoTheme.spacing.group).size(KindIconSize),
                    tint = ItmoTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        row.title.asString(),
                        color = ItmoTheme.colorScheme.onSurface,
                        style = ItmoTheme.typography.titleMedium,
                    )
                    listOfNotNull(row.connected, row.lastUsed).forEach { line ->
                        Text(
                            line.asString(),
                            Modifier.padding(top = ItmoTheme.spacing.related),
                            color = ItmoTheme.colorScheme.onSurfaceVariant,
                            style = ItmoTheme.typography.bodyMedium,
                        )
                    }
                    if (row.itmoSession) {
                        Pill(
                            stringResource(Res.string.connections_itmo_badge),
                            Modifier.padding(top = ItmoTheme.spacing.compact),
                        )
                    }
                }
            }
            ProgressButton(
                label = stringResource(Res.string.connections_revoke),
                onClick = { actions.onRevoke(row) },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = ItmoTheme.spacing.related)
                    .testTag(ConnectionsTestTags.revoke(row.id.toString())),
                style = ProgressButtonStyle.Text,
                enabled = enabled,
            )
        }
    }
}

/** «Отключить «nas»?» with what ends and what does not; [windowed] false draws it without its window, for previews. */
@Composable
internal fun RevokeDialog(row: ConnectionRow, actions: ConnectionsActions, windowed: Boolean = true) {
    val title = row.revokeTitle.asString()
    val text = row.revokeText.asString()
    val confirm = stringResource(Res.string.connections_revoke)
    val dismiss = stringResource(CoreRes.string.common_cancel)
    if (windowed) {
        ConfirmDialog(
            title, confirm, dismiss, actions.onConfirmRevoke, actions.onDismissRevoke, text = text, destructive = true,
        )
    } else {
        ConfirmDialogSurface(
            title, confirm, dismiss, actions.onConfirmRevoke, actions.onDismissRevoke, text = text, destructive = true,
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

private val KindIconSize = 24.dp
