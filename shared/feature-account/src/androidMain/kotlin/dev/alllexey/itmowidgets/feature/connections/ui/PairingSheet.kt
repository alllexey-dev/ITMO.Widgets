package dev.alllexey.itmowidgets.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicatorSize
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetClose
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.presentation.GrantSwitch
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.ResultAction
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_check_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code_scanner
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import org.jetbrains.compose.resources.painterResource

/**
 * «Подключить устройство» (UX spec section 5): [onScan] starts the platform scanner, [unlock] is the screen lock,
 * [onClose] closes the sheet. The ITMO.ID page of an agent's or MCP server's sign-in covers the screen.
 */
@Composable
fun PairingSheetRoute(viewModel: PairingViewModel, unlock: DeviceUnlock, onScan: () -> Unit, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val close = SheetClose(text(CoreStrings.common_close), onClose)
    when (val current = state) {
        is PairingUiState.SigningIn -> ClientSignInPage(
            url = current.url,
            userAgent = current.userAgent,
            title = current.title.asString(),
            readIdentity = current.readIdentity,
            isCallback = viewModel::isSignInCallback,
            onCallback = viewModel::onSignInCallback,
            onClose = viewModel::onSignInCancelled,
            onUnsupported = viewModel::onSignInUnsupported,
        )
        is PairingUiState.Confirm -> if (current.renewal) {
            LaunchedEffect(current) { if (!current.busy) viewModel.proceed(unlock) }
            Progress(close)
        } else {
            PairingConfirm(
                current,
                close,
                onProceed = { viewModel.proceed(unlock) },
                onScope = viewModel::onScopeChanged,
                onCancel = onClose,
                onGrant = viewModel::onGrantChanged,
                onConfirmGrant = viewModel::confirmGrant,
                onDismissGrant = viewModel::dismissGrant,
            )
        }
        else -> Unit
    }
    when (val current = state) {
        is PairingUiState.Input -> PairingInput(current, close, viewModel::onTextChanged, viewModel::submit, onScan)
        PairingUiState.Opening, PairingUiState.Sending -> Progress(close)
        is PairingUiState.Result -> PairingResultContent(current, close) {
            if (current.next == ResultAction.CLOSE) onClose() else viewModel.onResultAction()
        }
        is PairingUiState.SigningIn -> Progress(close)
        is PairingUiState.Confirm -> Unit
    }
}

@Composable
internal fun PairingInput(
    state: PairingUiState.Input,
    close: SheetClose,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onScan: () -> Unit,
) = SheetScaffold(
    title = text(AccountStrings.pair_title),
    close = close,
    footer = {
        ProgressButton(
            text(AccountStrings.pair_scan),
            onClick = onScan,
            modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).fillMaxWidth().testTag(PairingTestTags.SCAN),
            icon = painterResource(Res.drawable.ic_qr_code_scanner),
        )
        ProgressButton(
            text(AccountStrings.pair_open),
            onClick = onSubmit,
            modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).fillMaxWidth().testTag(PairingTestTags.OPEN),
            style = ProgressButtonStyle.Text,
            enabled = state.text.isNotBlank(),
        )
    },
) {
    OutlinedTextField(
        value = state.text,
        onValueChange = onText,
        modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).fillMaxWidth().testTag(PairingTestTags.FIELD),
        label = { Text(text(AccountStrings.pair_paste_hint)) },
        isError = state.error != null,
        supportingText = state.error?.let { error -> { Text(error.asString()) } },
        singleLine = true,
    )
}

@Composable
internal fun PairingConfirm(
    state: PairingUiState.Confirm,
    close: SheetClose,
    onProceed: () -> Unit,
    onScope: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onGrant: (String, Boolean) -> Unit = { _, _ -> },
    onConfirmGrant: () -> Unit = {},
    onDismissGrant: () -> Unit = {},
) = SheetScaffold(
    title = text(AccountStrings.pair_title),
    close = close,
    footer = {
        ProgressButton(
            state.primary.asString(),
            onClick = onProceed,
            modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).fillMaxWidth().testTag(PairingTestTags.PROCEED),
            inProgress = state.busy,
            enabled = state.canProceed,
        )
        ProgressButton(
            text(CoreStrings.common_cancel),
            onClick = onCancel,
            modifier = Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).fillMaxWidth(),
            style = ProgressButtonStyle.Text,
            enabled = !state.busy,
        )
    },
) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        ItmoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content), verticalAlignment = Alignment.CenterVertically) {
                IconTile(state.icon.painter(), state.icon.tone())
                Column(Modifier.weight(1f)) {
                    Text(state.name.asString(), color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
                    Text(state.subtitle.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
                }
            }
        }
        if (state.caution) {
            ItmoCard(Modifier.testTag(PairingTestTags.CAUTION)) {
                Text(text(AccountStrings.pair_caution_title), style = ItmoTheme.typography.labelLarge)
                Text(text(AccountStrings.pair_caution_text), style = ItmoTheme.typography.bodySmall)
            }
        }
        if (state.scopes.isNotEmpty()) {
            SectionHeader(text(AccountStrings.pair_access))
            ListGroup {
                state.scopes.forEach { scope ->
                    item {
                        SwitchListRow(
                            scope.title.asString(),
                            checked = scope.checked,
                            onCheckedChange = { checked -> scope.id?.let { onScope(it, checked) } },
                            subtitle = scope.subtitle.asString(),
                            enabled = scope.id != null && !state.busy,
                        )
                    }
                }
            }
        }
        state.grants.forEach { group ->
            SectionHeader(group.title.asString())
            ListGroup(Modifier.testTag(PairingTestTags.GRANTS)) {
                group.grants.forEach { grant -> item { GrantRow(grant, enabled = grant.enabled && !state.busy, onGrant) } }
            }
        }
        ListGroupNote(state.note.asString())
    }
    state.ask?.let { ConfirmAskDialog(it, onConfirmGrant, onDismissGrant) }
}

/**
 * A grant switch: the title, what it gives, «Зачем: …», then «Нужно для работы» or «Недоступно в этой версии», and the
 * warning of `itmo:sso` in the error colour.
 */
@Composable
private fun GrantRow(grant: GrantSwitch, enabled: Boolean, onGrant: (String, Boolean) -> Unit) = Row(
    Modifier
        .fillMaxWidth()
        .toggleable(grant.checked, enabled, Role.Switch) { onGrant(grant.id, it) }
        .padding(start = ItmoTheme.spacing.cardPadding, end = ItmoTheme.spacing.content)
        .padding(vertical = ItmoTheme.spacing.content)
        .testTag(PairingTestTags.grant(grant.id)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GrantLineGap)) {
        Text(
            grant.title.asString(),
            color = if (grant.enabled || grant.checked) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodyLarge,
        )
        listOfNotNull(grant.description, grant.purpose).forEach {
            Text(it.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        }
        grant.note?.let { Text(it.asString(), color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelMedium) }
        grant.warning?.let { Text(it.asString(), color = ItmoTheme.colorScheme.error, style = ItmoTheme.typography.bodyMedium) }
    }
    Switch(
        checked = grant.checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent = if (grant.checked) {
            { Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(SwitchDefaults.IconSize)) }
        } else {
            null
        },
    )
}

@Composable
private fun Progress(close: SheetClose) = SheetScaffold(title = text(AccountStrings.pair_title), close = close) {
    Box(Modifier.fillMaxWidth().heightIn(min = ProgressHeight), contentAlignment = Alignment.Center) {
        ItmoActivityIndicator(size = ItmoActivityIndicatorSize.Large)
    }
}

@Composable
internal fun PairingResultContent(state: PairingUiState.Result, close: SheetClose, onAction: () -> Unit) =
    SheetScaffold(title = text(AccountStrings.pair_title), close = close) {
        Box(Modifier.fillMaxWidth().padding(bottom = ItmoTheme.spacing.section).testTag(PairingTestTags.RESULT)) {
            ContentState(
                title = state.title.asString(),
                size = ContentStateSize.Compact,
                icon = painterResource(if (state.success) Res.drawable.ic_check_circle else Res.drawable.ic_error),
                description = state.text.asString(),
                action = ContentStateAction(state.action.asString(), onAction),
            )
        }
    }

private val ProgressHeight = 160.dp
private val GrantLineGap = 2.dp

/** Test tags of the pairing sheet. */
object PairingTestTags {
    const val FIELD = "pairing_field"
    const val SCAN = "pairing_scan"
    const val OPEN = "pairing_open"
    const val CAUTION = "pairing_caution"
    const val PROCEED = "pairing_proceed"
    const val RESULT = "pairing_result"
    const val SIGN_IN = "pairing_sign_in"
    const val GRANTS = "pairing_grants"

    fun grant(id: String): String = "pairing_grant:$id"
}
