package dev.alllexey.itmowidgets.feature.weblogin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicatorSize
import dev.alllexey.itmowidgets.designsystem.components.sheets.SheetScaffold
import dev.alllexey.itmowidgets.designsystem.components.state.ContentState
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateAction
import dev.alllexey.itmowidgets.designsystem.components.state.ContentStateSize
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.ItmoCard
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ScopeChoice
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginUiState
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import dev.alllexey.itmowidgets.shared.compose.strings.Res as CoreRes
import dev.alllexey.itmowidgets.shared.compose.strings.common_cancel
import dev.alllexey.itmowidgets.shared.compose.strings.common_close
import dev.alllexey.itmowidgets.shared.compose.strings.common_retry
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_check_circle
import dev.alllexey.itmowidgets.shared.designsystem.ic_code_blocks
import dev.alllexey.itmowidgets.shared.designsystem.ic_computer
import dev.alllexey.itmowidgets.shared.designsystem.ic_error
import dev.alllexey.itmowidgets.shared.designsystem.ic_qr_code_scanner
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.connections_title
import dev.alllexey.itmowidgets.shared.feature.account.pair_rotating
import dev.alllexey.itmowidgets.shared.feature.account.pair_sending
import dev.alllexey.itmowidgets.shared.feature.account.pair_shared_session
import dev.alllexey.itmowidgets.shared.feature.account.pair_unlocking
import dev.alllexey.itmowidgets.shared.feature.account.web_login_code_hint
import dev.alllexey.itmowidgets.shared.feature.account.web_login_continue
import dev.alllexey.itmowidgets.shared.feature.account.web_login_scan
import dev.alllexey.itmowidgets.shared.feature.account.web_login_title
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * What the «Подключить устройство» sheet asks of its host. [onScan] starts the platform's QR scanner: Android's Play
 * services scanner answers the ViewModel's `onScanned` or `onScannerUnavailable` itself. [onClose] closes the sheet
 * after `Done`; [onOpenConnections] opens «Подключённые устройства». The rest are the ViewModel's own calls, which
 * [WebLoginSheetRoute] wires.
 */
@Immutable
class WebLoginActions(
    val onCodeChanged: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onApprove: () -> Unit = {},
    val onScopeChanged: (AgentScope, Boolean) -> Unit = { _, _ -> },
    val onEditCode: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenConnections: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/**
 * The «Подключить устройство» sheet over [viewModel]. The host passes its own ViewModel, since the Android scanner
 * answers it after the sheet's view may be gone, the platform's screen lock [unlock], and performs [onScan],
 * [onOpenConnections] and [onClose].
 */
@Composable
fun WebLoginSheetRoute(
    onScan: () -> Unit,
    onOpenConnections: () -> Unit,
    onClose: () -> Unit,
    unlock: DeviceUnlock,
    modifier: Modifier = Modifier,
    viewModel: WebLoginViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onScan, onOpenConnections, onClose, unlock) {
        WebLoginActions(
            onCodeChanged = viewModel::onCodeChanged,
            onSubmit = viewModel::submit,
            onScan = onScan,
            onApprove = { viewModel.approve(unlock) },
            onScopeChanged = viewModel::onScopeChanged,
            onEditCode = viewModel::editCode,
            onRetry = viewModel::retry,
            onOpenConnections = onOpenConnections,
            onClose = onClose,
        )
    }
    WebLoginSheetContent(state, actions, modifier)
}

/**
 * The body of the «Подключить устройство» sheet: the title, then one step. `Input` and `Checking` show
 * `Сканировать QR`, the `Код с сайта` field with its error, `Продолжить` and the way to «Подключённые устройства»,
 * all disabled with the progress inside `Продолжить` while the code is checked. `Confirm` shows the device card (its
 * kind, own name, browser or version and time), what it gets, an agent's permissions, the shared-session notice and
 * the verification code for a device that receives the ITMO session, the warning, then the approval and `Отмена`.
 * `Unlocking`, `Rotating` and `Sending` show the progress; `Done` and `Error` are the compact content state with
 * `Закрыть` or `Повторить`. The host owns the sheet's container and height.
 */
@Composable
fun WebLoginSheetContent(
    state: WebLoginUiState,
    actions: WebLoginActions,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(title = stringResource(Res.string.web_login_title), modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (state) {
                is WebLoginUiState.Input -> CodeStep(state.code, state.error?.asString(), checking = false, actions)
                is WebLoginUiState.Checking -> CodeStep(state.code, error = null, checking = true, actions)
                is WebLoginUiState.Confirm -> ConfirmStep(state, actions)
                WebLoginUiState.Unlocking -> ProgressStep(stringResource(Res.string.pair_unlocking))
                WebLoginUiState.Rotating -> ProgressStep(stringResource(Res.string.pair_rotating))
                WebLoginUiState.Sending -> ProgressStep(stringResource(Res.string.pair_sending))
                is WebLoginUiState.Done -> ResultStep(
                    icon = KitRes.drawable.ic_check_circle,
                    text = state.text.asString(),
                    action = ContentStateAction(stringResource(CoreRes.string.common_close), actions.onClose,
                        modifier = Modifier.testTag(WebLoginSheetTestTags.RESULT_ACTION)),
                )
                is WebLoginUiState.Error -> ResultStep(
                    icon = KitRes.drawable.ic_error,
                    text = state.text.asString(),
                    action = ContentStateAction(stringResource(CoreRes.string.common_retry), actions.onRetry,
                        modifier = Modifier.testTag(WebLoginSheetTestTags.RESULT_ACTION)),
                )
            }
        }
    }
}

/** The scanner first, then the field for a code typed from the screen, then `Продолжить`. */
@Composable
private fun CodeStep(code: String, error: String?, checking: Boolean, actions: WebLoginActions) {
    val field = remember { CodeFieldValue(code) }
    ProgressButton(
        label = stringResource(Res.string.web_login_scan),
        onClick = actions.onScan,
        modifier = Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(WebLoginSheetTestTags.SCAN),
        enabled = !checking,
        icon = painterResource(KitRes.drawable.ic_qr_code_scanner),
    )
    OutlinedTextField(
        value = field.follow(code),
        onValueChange = { value ->
            field.value = value
            if (value.text != code) actions.onCodeChanged(value.text)
        },
        modifier = Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.group)
            .testTag(WebLoginSheetTestTags.CODE),
        enabled = !checking,
        label = { Text(stringResource(Res.string.web_login_code_hint)) },
        trailingIcon = error?.let { { Icon(painterResource(KitRes.drawable.ic_error), contentDescription = null) } },
        supportingText = error?.let { { Text(it, Modifier.testTag(WebLoginSheetTestTags.CODE_ERROR)) } },
        isError = error != null,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { actions.onSubmit() }),
        singleLine = true,
    )
    ProgressButton(
        label = stringResource(Res.string.web_login_continue),
        onClick = actions.onSubmit,
        modifier = Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(WebLoginSheetTestTags.CONTINUE),
        style = ProgressButtonStyle.Tonal,
        inProgress = checking,
        enabled = !checking && code.isNotBlank(),
    )
    ProgressButton(
        label = stringResource(Res.string.connections_title),
        onClick = actions.onOpenConnections,
        modifier = Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.related)
            .testTag(WebLoginSheetTestTags.CONNECTIONS),
        style = ProgressButtonStyle.Text,
        enabled = !checking,
    )
}

/**
 * The device card (kind, own name, browser or version, time), what it gets, an agent's permissions, the
 * shared-session notice and the verification code, the warning, then the approval and `Отмена`.
 */
@Composable
private fun ConfirmStep(state: WebLoginUiState.Confirm, actions: WebLoginActions) {
    ItmoCard(
        Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.compact)
            .testTag(WebLoginSheetTestTags.BROWSER),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content), verticalAlignment = Alignment.CenterVertically) {
            IconTile(painterResource(kindIcon(state.kind)), Tone.Primary)
            Column(Modifier.weight(1f)) {
                Text(
                    state.title.asString(),
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.titleMedium,
                )
                listOfNotNull(state.name, state.client, state.requestedAt).forEach { line ->
                    Text(
                        line.asString(),
                        Modifier.padding(top = ItmoTheme.spacing.related),
                        color = ItmoTheme.colorScheme.onSurfaceVariant,
                        style = ItmoTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
    Text(
        state.access.asString(),
        Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.group),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.bodyMedium,
    )
    if (state.scopes.isNotEmpty()) {
        ListGroup(Modifier.stepMargin().padding(top = ItmoTheme.spacing.compact)) {
            state.scopes.forEach { choice -> item { ScopeRow(choice, state.scopesEditable && !state.approving, actions) } }
        }
    }
    if (state.sharedSession) {
        Text(
            stringResource(Res.string.pair_shared_session),
            Modifier
                .stepMargin()
                .padding(top = ItmoTheme.spacing.compact),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
    }
    state.verification?.let { verification ->
        ItmoCard(
            Modifier
                .stepMargin()
                .padding(top = ItmoTheme.spacing.group)
                .testTag(WebLoginSheetTestTags.VERIFICATION),
            tone = Tone.Tertiary,
        ) {
            Text(verification.asString(), style = ItmoTheme.typography.titleSmall)
        }
    }
    Text(
        state.warning.asString(),
        Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.compact),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodySmall,
    )
    ProgressButton(
        label = state.approve.asString(),
        onClick = actions.onApprove,
        modifier = Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.group)
            .testTag(WebLoginSheetTestTags.APPROVE),
        inProgress = state.approving,
        enabled = state.canApprove,
    )
    ProgressButton(
        label = stringResource(CoreRes.string.common_cancel),
        onClick = actions.onEditCode,
        modifier = Modifier
            .stepMargin()
            .padding(top = ItmoTheme.spacing.related)
            .testTag(WebLoginSheetTestTags.CANCEL),
        style = ProgressButtonStyle.Text,
        enabled = !state.approving,
    )
}

/** A browser for the web, a client for every other kind. */
internal fun kindIcon(kind: PairingKind): DrawableResource =
    if (kind == PairingKind.WEB_SESSION) KitRes.drawable.ic_computer else KitRes.drawable.ic_code_blocks

/** One permission: a switch for a new agent, a shown state for an agent session. The whole row is the toggle. */
@Composable
private fun ScopeRow(choice: ScopeChoice, editable: Boolean, actions: WebLoginActions) {
    SwitchListRow(
        choice.title.asString(),
        checked = choice.checked,
        onCheckedChange = { actions.onScopeChanged(choice.scope, it) },
        modifier = Modifier.testTag(WebLoginSheetTestTags.scope(choice.scope)),
        enabled = editable,
    )
}

/** The screen lock, the hand-off or the upload: a spinner and what happens. */
@Composable
private fun ProgressStep(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ItmoTheme.spacing.section)
            .testTag(WebLoginSheetTestTags.PROGRESS)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItmoActivityIndicator(size = ItmoActivityIndicatorSize.Large)
        Text(
            text,
            Modifier.padding(top = ItmoTheme.spacing.group),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
    }
}

/** Done and error share the compact content state; TalkBack reads the outcome when it appears. */
@Composable
private fun ResultStep(
    icon: DrawableResource,
    text: String,
    action: ContentStateAction,
) {
    ContentState(
        title = text,
        modifier = Modifier
            .testTag(WebLoginSheetTestTags.RESULT)
            .semantics { liveRegion = LiveRegionMode.Polite },
        size = ContentStateSize.Compact,
        icon = painterResource(icon),
        action = action,
    )
}

/** Full width between the screen margins, as every row of `sheet_web_login.xml`. */
@Composable
private fun Modifier.stepMargin(): Modifier = fillMaxWidth().padding(horizontal = ItmoTheme.spacing.screenMargin)

/**
 * The field's text and cursor. It follows the state's code only when the state changes it (a scanned code, the
 * normalized code while checking), and then puts the cursor at the end; a keystroke the state has not caught up with
 * yet is never overwritten.
 */
@Stable
private class CodeFieldValue(code: String) {
    private var followed = code
    var value by mutableStateOf(code.atEnd())

    fun follow(code: String): TextFieldValue {
        if (code != followed) {
            followed = code
            if (value.text != code) value = code.atEnd()
        }
        return value
    }

    private fun String.atEnd() = TextFieldValue(this, TextRange(length))
}

/** Tags for host tests. */
object WebLoginSheetTestTags {
    const val SCAN = "web_login_scan_button"
    const val CODE = "web_login_code"
    const val CODE_ERROR = "web_login_code_error"
    const val CONTINUE = "web_login_continue_button"
    const val BROWSER = "web_login_browser_card"
    const val APPROVE = "web_login_approve_button"
    const val CANCEL = "web_login_cancel_button"
    const val RESULT = "web_login_result"
    const val RESULT_ACTION = "web_login_result_button"
    const val CONNECTIONS = "web_login_connections_button"
    const val VERIFICATION = "web_login_verification"
    const val PROGRESS = "web_login_progress"

    fun scope(scope: AgentScope): String = "web_login_scope:${scope.name}"
}

