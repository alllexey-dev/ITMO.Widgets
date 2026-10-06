package dev.alllexey.itmowidgets.feature.debug.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBar
import dev.alllexey.itmowidgets.designsystem.components.bars.AppTopBarBack
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButton
import dev.alllexey.itmowidgets.designsystem.components.buttons.ProgressButtonStyle
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoSwitch
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.debug.presentation.DebugToolsUiState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import com.google.android.material.R as MaterialR

/** What the debug tools ask of their host; each call maps to one `DebugToolsViewModel` action or to leaving. */
interface DebugToolsActions {
    fun back()
    fun replaceRefreshToken(refreshToken: String)
    fun setDateOverride(date: LocalDate?)
    fun setScoreOverride(attendances: Int, bonus: Int)
    fun clearScoreOverride()
    fun setLessonTemplatesEnabled(enabled: Boolean)
    fun setCustomServicesEnabled(enabled: Boolean)
    fun checkScheduleChanges()
    fun checkMarks()
    fun probeBarsSession()
}

/** Tags the host flow tests find the screen by. */
object DebugToolsTestTags {
    const val BACK = "debug_tools_back"
}

/** The dialog the screen shows over itself; a saved name, so it survives recreation (never with its input). */
private enum class DebugToolsDialog { RefreshToken, Date, SportScore }

/**
 * The debug tools: refresh token, academic date, sport score and lesson-template overrides, and the one-off
 * background checks. Every section is debug-only; the host never shows the screen in a release build. Its dialogs
 * are part of the screen; the token typed into one stays in memory only.
 */
@Composable
fun DebugToolsScreen(state: DebugToolsUiState, actions: DebugToolsActions, modifier: Modifier = Modifier) {
    when (state) {
        is DebugToolsUiState.Content -> DebugToolsContent(state, actions, modifier)
    }
}

@Composable
private fun DebugToolsContent(state: DebugToolsUiState.Content, actions: DebugToolsActions, modifier: Modifier) {
    var dialog by rememberSaveable { mutableStateOf<DebugToolsDialog?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        AppTopBar(
            title = stringResource(R.string.debug_tools_title),
            navigation = {
                AppTopBarBack(
                    label = stringResource(R.string.settings_back),
                    onClick = actions::back,
                    modifier = Modifier.testTag(DebugToolsTestTags.BACK),
                )
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ItmoTheme.spacing.cardPadding),
        ) {
            // This user-facing opt-in now lives in the regular settings screen.
            if (CUSTOM_SERVICES_VISIBLE) {
                ToggleCard(
                    title = stringResource(R.string.debug_custom_services_title),
                    description = stringResource(R.string.debug_custom_services_description),
                    checked = state.customServicesEnabled,
                    onCheckedChange = actions::setCustomServicesEnabled,
                )
            }
            RefreshTokenCard(state, onConfigure = { dialog = DebugToolsDialog.RefreshToken })
            OverrideCard(
                title = stringResource(R.string.debug_academic_time_title),
                description = stringResource(R.string.debug_academic_time_description),
                value = dateValue(state.dateOverride),
                actionLabel = stringResource(R.string.debug_academic_time_select),
                onAction = { dialog = DebugToolsDialog.Date },
                onReset = { actions.setDateOverride(null) }.takeIf { state.dateOverride != null },
            )
            OverrideCard(
                title = stringResource(R.string.debug_sport_score_title),
                description = stringResource(R.string.debug_sport_score_description),
                value = scoreValue(state.scoreOverride),
                actionLabel = stringResource(R.string.debug_sport_score_configure),
                onAction = { dialog = DebugToolsDialog.SportScore },
                onReset = actions::clearScoreOverride.takeIf { state.scoreOverride != null },
            )
            ToggleCard(
                title = stringResource(R.string.debug_sport_lessons_title),
                description = stringResource(R.string.debug_sport_lessons_description),
                checked = state.lessonTemplatesEnabled,
                onCheckedChange = actions::setLessonTemplatesEnabled,
            )
            ChecksCard(actions)
        }
    }
    val close = { dialog = null }
    when (dialog) {
        DebugToolsDialog.RefreshToken -> Dialog(onDismissRequest = close) {
            RefreshTokenDialog(
                onSave = { token ->
                    actions.replaceRefreshToken(token)
                    close()
                },
                onDismiss = close,
            )
        }
        DebugToolsDialog.SportScore -> Dialog(onDismissRequest = close) {
            SportScoreDialog(
                current = state.scoreOverride,
                onSave = { attendances, bonus ->
                    actions.setScoreOverride(attendances, bonus)
                    close()
                },
                onDismiss = close,
            )
        }
        DebugToolsDialog.Date -> AcademicDateDialog(
            initial = state.dateOverride ?: state.effectiveDate,
            onPick = { date ->
                actions.setDateOverride(date)
                close()
            },
            onDismiss = close,
        )
        null -> Unit
    }
}

@Composable
private fun RefreshTokenCard(state: DebugToolsUiState.Content, onConfigure: () -> Unit) {
    DebugCard {
        CardHeading(
            stringResource(R.string.debug_refresh_token_title),
            stringResource(R.string.debug_refresh_token_description),
        )
        CardValue(
            stringResource(
                if (state.refreshTokenConfigured) {
                    R.string.debug_refresh_token_configured
                } else {
                    R.string.debug_refresh_token_not_configured
                },
            ),
        )
        val label = when {
            state.refreshTokenUpdateInProgress -> R.string.debug_refresh_token_checking
            state.refreshTokenConfigured -> R.string.debug_refresh_token_replace
            else -> R.string.debug_refresh_token_add
        }
        ProgressButton(
            stringResource(label),
            onClick = onConfigure,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = ItmoTheme.spacing.content),
            enabled = !state.refreshTokenUpdateInProgress,
        )
    }
}

/** A value with "reset" (disabled while [onReset] is null) and an action that opens a dialog. */
@Composable
private fun OverrideCard(
    title: String,
    description: String,
    value: String,
    actionLabel: String,
    onAction: () -> Unit,
    onReset: (() -> Unit)?,
) {
    DebugCard {
        CardHeading(title, description)
        CardValue(value)
        Row(
            Modifier
                .align(Alignment.End)
                .padding(top = ItmoTheme.spacing.content),
            horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressButton(
                stringResource(R.string.debug_academic_time_reset),
                onClick = { onReset?.invoke() },
                style = ProgressButtonStyle.Text,
                enabled = onReset != null,
            )
            ProgressButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun ToggleCard(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DebugCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = ItmoTheme.spacing.group),
            ) {
                CardHeading(title, description)
            }
            ItmoSwitch(checked, onCheckedChange, Modifier.semantics { contentDescription = title })
        }
    }
}

@Composable
private fun ChecksCard(actions: DebugToolsActions) {
    DebugCard(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
        listOf(
            R.string.debug_schedule_changes_check to actions::checkScheduleChanges,
            R.string.debug_marks_check to actions::checkMarks,
            R.string.debug_bars_session_probe to actions::probeBarsSession,
        ).forEach { (label, onClick) ->
            ProgressButton(
                stringResource(label),
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                style = ProgressButtonStyle.Tonal,
            )
        }
    }
}

/** `Widget.ItmoWidgets.Card.Content.Outlined`, [ItmoTheme]'s content card with its outline. */
@Composable
private fun DebugCard(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.content),
        shape = ItmoTheme.shapes.cardContent,
        color = ItmoTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(ItmoTheme.shapes.cardStroke, ItmoTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(ItmoTheme.spacing.cardPadding), verticalArrangement, content = content)
    }
}

@Composable
private fun CardHeading(title: String, description: String) {
    Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleMedium)
    Text(
        description,
        Modifier.padding(top = ItmoTheme.spacing.related),
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodyMedium,
    )
}

@Composable
private fun CardValue(value: String) {
    Text(
        value,
        Modifier.padding(top = ItmoTheme.spacing.content),
        color = ItmoTheme.colorScheme.onSurface,
        style = ItmoTheme.typography.bodyLarge,
    )
}

@Composable
private fun dateValue(date: LocalDate?): String = if (date == null) {
    stringResource(R.string.debug_academic_time_system)
} else {
    val formatted = remember(date) {
        date.toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
    stringResource(R.string.debug_academic_time_value, formatted)
}

@Composable
private fun scoreValue(score: SportScoreOverride?): String = if (score == null) {
    stringResource(R.string.debug_sport_score_server)
} else {
    stringResource(R.string.debug_sport_score_value, score.attendances, score.bonus)
}

/**
 * Asks for a refresh token. [onSave] gets a non-blank token; a blank one shows the error instead. The field is kept
 * in plain `remember`, so the token never reaches saved state.
 */
@Composable
internal fun RefreshTokenDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    DebugDialogSurface(
        title = stringResource(R.string.debug_refresh_token_dialog_title),
        confirmLabel = stringResource(R.string.debug_refresh_token_save),
        onConfirm = {
            if (token.isBlank()) {
                missing = true
            } else {
                val entered = token
                token = ""
                onSave(entered)
            }
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.debug_refresh_token_hint)) },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        painterResource(
                            if (visible) MaterialR.drawable.design_ic_visibility_off else MaterialR.drawable.design_ic_visibility,
                        ),
                        contentDescription = stringResource(MaterialR.string.password_toggle_content_description),
                    )
                }
            },
            supportingText = if (missing) {
                { Text(stringResource(R.string.debug_refresh_token_required)) }
            } else {
                null
            },
            isError = missing,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            minLines = 1,
            maxLines = TOKEN_MAX_LINES,
        )
    }
}

/**
 * Asks for both sport scores, prefilled from [current] or the defaults. [onSave] gets two values in [SCORE_RANGE];
 * an invalid field shows its error instead.
 */
@Composable
internal fun SportScoreDialog(
    current: SportScoreOverride?,
    onSave: (attendances: Int, bonus: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var attendances by rememberSaveable { mutableStateOf((current?.attendances ?: DEFAULT_ATTENDANCE_POINTS).toString()) }
    var bonus by rememberSaveable { mutableStateOf((current?.bonus ?: DEFAULT_BONUS_POINTS).toString()) }
    var attendancesInvalid by rememberSaveable { mutableStateOf(false) }
    var bonusInvalid by rememberSaveable { mutableStateOf(false) }
    DebugDialogSurface(
        title = stringResource(R.string.debug_sport_score_dialog_title),
        confirmLabel = stringResource(android.R.string.ok),
        onConfirm = {
            val attendancesValue = attendances.toIntOrNull()?.takeIf { it in SCORE_RANGE }
            val bonusValue = bonus.toIntOrNull()?.takeIf { it in SCORE_RANGE }
            attendancesInvalid = attendancesValue == null
            bonusInvalid = bonusValue == null
            if (attendancesValue != null && bonusValue != null) onSave(attendancesValue, bonusValue)
        },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content)) {
            ScoreField(attendances, { attendances = it }, R.string.debug_sport_score_attendance_hint, attendancesInvalid)
            ScoreField(bonus, { bonus = it }, R.string.debug_sport_score_bonus_hint, bonusInvalid)
        }
    }
}

@Composable
private fun ScoreField(value: String, onValueChange: (String) -> Unit, label: Int, invalid: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(SCORE_MAX_DIGITS)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        supportingText = if (invalid) {
            { Text(stringResource(R.string.debug_sport_score_invalid_value)) }
        } else {
            null
        },
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

/** material3's date picker on the effective academic date; the picked day comes back as a kotlinx [LocalDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcademicDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker counts UTC midnights in milliseconds.
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDays() * MILLIS_PER_DAY)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = picker.selectedDateMillis
                    if (millis == null) onDismiss() else onPick(LocalDate.fromEpochDays(millis / MILLIS_PER_DAY))
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    ) {
        DatePicker(picker)
    }
}

/**
 * The alert dialog surface of the two input dialogs, the kit dialogs' layout (material3 `AlertDialog`: 28 dp corners
 * in `surfaceContainerHigh`, 24 dp padding, the title in `headlineSmall`, the buttons at the end) around an input.
 * The window is the caller's, so previews draw the surface alone.
 */
@Composable
private fun DebugDialogSurface(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val padding = ItmoTheme.spacing.section
    Surface(
        Modifier.sizeIn(minWidth = DialogMinWidth, maxWidth = DialogMaxWidth),
        shape = ItmoTheme.shapes.extraLarge,
        color = ItmoTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(padding)) {
            Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.headlineSmall)
            Column(Modifier.padding(top = ItmoTheme.spacing.group)) { content() }
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(top = padding),
                horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = onConfirm) { Text(confirmLabel) }
            }
        }
    }
}

/** The custom-services switch stays in the screen, hidden, as in v2.2. */
private const val CUSTOM_SERVICES_VISIBLE = false

private const val DEFAULT_ATTENDANCE_POINTS = 100
private const val DEFAULT_BONUS_POINTS = 20
private val SCORE_RANGE = 0..9999
private const val SCORE_MAX_DIGITS = 4
private const val TOKEN_MAX_LINES = 4
private const val MILLIS_PER_DAY = 86_400_000L

/** material3's `DialogMinWidth` and `DialogMaxWidth`. */
private val DialogMinWidth = 280.dp
private val DialogMaxWidth = 560.dp
