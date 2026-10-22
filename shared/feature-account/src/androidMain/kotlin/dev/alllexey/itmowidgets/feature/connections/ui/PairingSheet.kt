package dev.alllexey.itmowidgets.feature.connections.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingUiState
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.ResultAction
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
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
            code = current.code,
            onNavigation = viewModel::onSignInNavigation,
            onClose = viewModel::onSignInCancelled,
            onUnsupported = viewModel::onSignInUnsupported,
        )
        is PairingUiState.Confirm -> if (current.renewal) {
            LaunchedEffect(current) { if (!current.busy) viewModel.proceed(unlock) }
            Progress(close)
        } else {
            PairingConfirm(current, close, onProceed = { viewModel.proceed(unlock) }, onScope = viewModel::onScopeChanged, onCancel = onClose)
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
        ItmoCard(Modifier.testTag(PairingTestTags.CODE)) {
            Text(text(AccountStrings.pair_code_title), style = ItmoTheme.typography.labelLarge)
            Text(
                state.code,
                Modifier.fillMaxWidth().padding(vertical = ItmoTheme.spacing.related),
                style = ItmoTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            Text(text(AccountStrings.pair_code_hint), style = ItmoTheme.typography.bodySmall)
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
        ListGroupNote(state.note.asString())
    }
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

/** Test tags of the pairing sheet. */
object PairingTestTags {
    const val FIELD = "pairing_field"
    const val SCAN = "pairing_scan"
    const val OPEN = "pairing_open"
    const val CODE = "pairing_code"
    const val PROCEED = "pairing_proceed"
    const val RESULT = "pairing_result"
    const val SIGN_IN = "pairing_sign_in"
}
