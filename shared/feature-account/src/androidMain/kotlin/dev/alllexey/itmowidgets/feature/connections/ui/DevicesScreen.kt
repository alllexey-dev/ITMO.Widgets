package dev.alllexey.itmowidgets.feature.connections.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.AppRefreshBox
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.components.state.Skeleton
import dev.alllexey.itmowidgets.designsystem.components.state.SkeletonStyle
import dev.alllexey.itmowidgets.designsystem.kit.GateCard
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ItmoSnackbarHost
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.ListRow
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.connections.presentation.DeviceRow
import dev.alllexey.itmowidgets.feature.connections.presentation.DevicesUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.DevicesViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.FactsSheet
import dev.alllexey.itmowidgets.feature.connections.presentation.OtherLogins
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_cloud_off
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_logout
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code_scanner
import dev.alllexey.itmowidgets.shared.designsystem.ic_visibility_off
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/** What «Устройства» asks of its host besides the ViewModel's calls. */
@Immutable
class DevicesActions(
    val onBack: () -> Unit = {},
    val onConnect: () -> Unit = {},
    val onEnableServer: () -> Unit = {},
    val onOpenClient: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onSheetAction: () -> Unit = {},
    val onEndAllOthers: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
    val onBrowserLike: (Boolean) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun DevicesRoute(
    viewModel: DevicesViewModel,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onEnableServer: () -> Unit,
    onOpenClient: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack, onConnect, onEnableServer, onOpenClient) {
        DevicesActions(
            onBack = onBack,
            onConnect = onConnect,
            onEnableServer = onEnableServer,
            onOpenClient = onOpenClient,
            onRefresh = viewModel::refresh,
            onOpen = viewModel::open,
            onCloseSheet = viewModel::closeSheet,
            onSheetAction = viewModel::askSheetAction,
            onEndAllOthers = viewModel::askEndAllOthers,
            onConfirm = viewModel::confirm,
            onDismissConfirm = viewModel::dismissConfirm,
            onBrowserLike = viewModel::setBrowserLike,
            onMessageShown = viewModel::onMessageShown,
        )
    }
    DevicesScreen(state, actions, modifier)
}

/**
 * «Устройства»: connect, this phone, the user's other installs, the connected clients, the other ITMO.ID sessions and
 * the label switch (UX spec section 2). Installs and sessions open facts sheets; clients open their screen.
 */
@Composable
fun DevicesScreen(state: DevicesUiState, actions: DevicesActions, modifier: Modifier = Modifier) {
    val snackbars = remember { SnackbarHostState() }
    ItmoDetailScreen(
        title = text(AccountStrings.devices_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.common_back),
        modifier = modifier,
    ) { padding ->
        // Below the header, so the pull indicator is not drawn under it.
        AppRefreshBox(state.refreshing, actions.onRefresh, Modifier.fillMaxSize().padding(padding)) {
            ScreenColumn(PaddingValues()) { DevicesContent(state, actions) }
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
    state.sheet?.let { sheet ->
        ConnectionsSheet(actions.onCloseSheet) { FactsSheetContent(sheet, state.busy, actions) }
    }
    state.confirm?.let { ConfirmAskDialog(it, actions.onConfirm, actions.onDismissConfirm) }
}

@Composable
private fun DevicesContent(state: DevicesUiState, actions: DevicesActions) {
    if (state.backend) {
        ListGroup(Modifier.testTag(DevicesTestTags.CONNECT)) {
            item {
                ListRow(
                    text(AccountStrings.devices_connect),
                    subtitle = text(AccountStrings.devices_connect_subtitle),
                    icon = painterResource(Res.drawable.ic_qr_code_scanner),
                    iconTone = Tone.Primary,
                    chevron = true,
                    onClick = actions.onConnect,
                )
            }
        }
    } else {
        GateCard(
            title = text(AccountStrings.devices_gate_title),
            reason = text(AccountStrings.devices_gate_reason),
            fix = text(AccountStrings.devices_gate_fix),
            onFix = actions.onEnableServer,
            icon = painterResource(Res.drawable.ic_cloud_off),
        )
    }
    state.thisPhone?.let { phone ->
        SectionHeader(text(AccountStrings.devices_this_phone))
        ListGroup { item { Row(phone, actions) } }
    }
    when {
        state.error != null -> ContentState(
            title = state.error.asString(),
            modifier = Modifier.fillMaxWidth().testTag(DevicesTestTags.ERROR),
            size = ContentStateSize.Compact,
            icon = painterResource(Res.drawable.ic_error),
            action = ContentStateAction(text(CoreStrings.common_retry), actions.onRefresh),
        )
        state.loading -> Skeleton(SkeletonStyle.List, rows = 2)
        else -> {
            if (state.installs.isNotEmpty()) {
                SectionHeader(text(AccountStrings.devices_my_devices))
                ListGroup { state.installs.forEach { row -> item { Row(row, actions) } } }
            }
            if (state.clients.isNotEmpty()) {
                SectionHeader(text(AccountStrings.devices_connected), count = state.clients.size.toString())
                ListGroup(Modifier.testTag(DevicesTestTags.CLIENTS)) {
                    state.clients.forEach { row -> item { Row(row, actions) } }
                }
            }
        }
    }
    Others(state.others, actions)
    ListGroup {
        item {
            SwitchListRow(
                text(AccountStrings.devices_browser_like),
                checked = state.browserLike,
                onCheckedChange = actions.onBrowserLike,
                modifier = Modifier.testTag(DevicesTestTags.BROWSER_LIKE),
                subtitle = text(AccountStrings.devices_browser_like_subtitle),
                icon = painterResource(Res.drawable.ic_visibility_off),
                iconTone = Tone.Neutral,
            )
        }
    }
}

@Composable
private fun Others(others: OtherLogins, actions: DevicesActions) {
    val count = (others as? OtherLogins.Content)?.rows?.size
    if (count == 0) return
    SectionHeader(text(AccountStrings.devices_others), count = count?.toString())
    when (others) {
        OtherLogins.Loading -> Skeleton(SkeletonStyle.List, rows = 3)
        OtherLogins.NoCookie -> ListGroupNote(text(AccountStrings.devices_others_no_cookie))
        is OtherLogins.Error -> ContentState(
            title = others.text.asString(),
            modifier = Modifier.fillMaxWidth(),
            size = ContentStateSize.Compact,
            icon = painterResource(Res.drawable.ic_error),
            action = ContentStateAction(text(CoreStrings.common_retry), actions.onRefresh),
        )
        is OtherLogins.Content -> ListGroup(Modifier.testTag(DevicesTestTags.OTHERS)) {
            others.rows.forEach { row -> item { Row(row, actions) } }
            item {
                ListRow(
                    text(AccountStrings.devices_end_all),
                    Modifier.testTag(DevicesTestTags.END_ALL),
                    icon = painterResource(Res.drawable.ic_logout),
                    iconTone = Tone.Neutral,
                    danger = true,
                    onClick = actions.onEndAllOthers,
                )
            }
        }
    }
}

@Composable
private fun Row(row: DeviceRow, actions: DevicesActions) {
    val client = row.connectionId
    ListRow(
        row.title.asString(),
        Modifier.testTag(DevicesTestTags.row(row.key)),
        subtitle = row.subtitle?.asString(),
        icon = row.icon.painter(),
        iconTone = row.icon.tone(),
        chevron = client != null,
        below = row.pill?.let { pill -> { PillView(pill) } },
        onClick = { if (client != null) actions.onOpenClient(client) else actions.onOpen(row.key) },
    )
}

@Composable
internal fun FactsSheetContent(sheet: FactsSheet, busy: Boolean, actions: DevicesActions) {
    SheetScaffold(
        title = sheet.title.asString(),
        subtitle = sheet.subtitle?.asString(),
        close = SheetClose(text(CoreStrings.common_close), actions.onCloseSheet),
        footer = sheet.action?.let { action ->
            {
                ProgressButton(
                    action.asString(),
                    onClick = actions.onSheetAction,
                    modifier = Modifier
                        .padding(horizontal = ItmoTheme.spacing.screenMargin)
                        .fillMaxWidth()
                        .testTag(DevicesTestTags.SHEET_ACTION),
                    style = ProgressButtonStyle.Outlined,
                    inProgress = busy,
                    icon = painterResource(Res.drawable.ic_logout),
                )
            }
        },
    ) {
        Column(Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin)) {
            FactsGroup(sheet.facts)
            sheet.note?.let { ListGroupNote(it.asString()) }
        }
    }
}

/** Test tags of [DevicesScreen]. */
object DevicesTestTags {
    const val CONNECT = "devices_connect"
    const val CLIENTS = "devices_clients"
    const val OTHERS = "devices_others"
    const val END_ALL = "devices_end_all"
    const val ERROR = "devices_error"
    const val BROWSER_LIKE = "devices_browser_like"
    const val SHEET_ACTION = "devices_sheet_action"

    fun row(key: String): String = "devices_row:$key"
}
