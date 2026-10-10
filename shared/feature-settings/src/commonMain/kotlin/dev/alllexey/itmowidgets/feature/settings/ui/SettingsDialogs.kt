package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.HexColor
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.designsystem.components.controls.ColorPicker
import dev.alllexey.itmowidgets.designsystem.components.controls.ColorPickerLabels
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.dialogs.SwatchChoiceDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.SwatchChoiceDialogSurface
import dev.alllexey.itmowidgets.designsystem.theme.accentSwatch
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_add
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_accent_color_done
import dev.alllexey.itmowidgets.shared.feature.settings.background_work_allow
import dev.alllexey.itmowidgets.shared.feature.settings.background_work_dialog_message
import dev.alllexey.itmowidgets.shared.feature.settings.background_work_later
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_allow
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_later
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_open_settings
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_rationale
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_background_work_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_custom_services_consent_message
import dev.alllexey.itmowidgets.shared.feature.settings.settings_custom_services_consent_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_custom_services_enable
import dev.alllexey.itmowidgets.shared.feature.settings.settings_color_picker_brightness
import dev.alllexey.itmowidgets.shared.feature.settings.settings_color_picker_hex
import dev.alllexey.itmowidgets.shared.feature.settings.settings_color_picker_hex_error
import dev.alllexey.itmowidgets.shared.feature.settings.settings_color_picker_hue
import dev.alllexey.itmowidgets.shared.feature.settings.settings_color_picker_preview
import dev.alllexey.itmowidgets.shared.feature.settings.settings_color_picker_saturation
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** A dialog of a settings page. Each one is saved by [SettingsDialogState], so it comes back after recreation. */
sealed interface SettingsDialog {

    /**
     * The single choice of the [SettingItem.Choice] row [id]: text size, the QR animation, the privacy audiences, the
     * colour swatches of the accent colour. It shows the row as the page has it now and is not drawn while the page
     * has no such row.
     */
    data class Choice(val id: SettingRowId) : SettingsDialog

    /** The custom-services consent, asked before they are turned on. */
    data object CustomServicesConsent : SettingsDialog

    /** The once-per-device offer to let the app work in the background. */
    data object BackgroundWorkHint : SettingsDialog

    /** Why the app asks for the calendar; [locked] after a refusal for good, when only the app's system page helps. */
    data class CalendarAccess(val locked: Boolean) : SettingsDialog
}

/**
 * The one dialog over a settings page. A host that opens dialogs from outside composition (the permission results
 * arrive before the first frame) keeps it itself and puts [save] into its saved state; the screen alone uses
 * [rememberSettingsDialogState].
 */
@Stable
class SettingsDialogState(initial: SettingsDialog? = null) {

    var dialog: SettingsDialog? by mutableStateOf(initial)
        private set

    fun show(dialog: SettingsDialog) {
        this.dialog = dialog
    }

    /** Closes the dialog and hands it out once, so a second tap before the window goes acts on nothing. */
    fun close(): SettingsDialog? = dialog.also { dialog = null }

    /** The shown dialog as a string for a saved state; null when none is shown. */
    fun save(): String? = when (val shown = dialog) {
        null -> null
        is SettingsDialog.Choice -> CHOICE_PREFIX + shown.id.name
        SettingsDialog.CustomServicesConsent -> CUSTOM_SERVICES
        SettingsDialog.BackgroundWorkHint -> BACKGROUND_WORK
        is SettingsDialog.CalendarAccess -> if (shown.locked) CALENDAR_ACCESS_LOCKED else CALENDAR_ACCESS
    }

    companion object {
        private const val CHOICE_PREFIX = "choice:"
        private const val CUSTOM_SERVICES = "custom-services"
        private const val BACKGROUND_WORK = "background-work"
        private const val CALENDAR_ACCESS = "calendar-access"
        private const val CALENDAR_ACCESS_LOCKED = "calendar-access-locked"

        /** The state [save] wrote; an unknown or missing value shows nothing. */
        fun restore(saved: String?): SettingsDialogState = SettingsDialogState(
            when {
                saved == null -> null
                saved.startsWith(CHOICE_PREFIX) -> SettingRowId.entries
                    .firstOrNull { it.name == saved.removePrefix(CHOICE_PREFIX) }
                    ?.let(SettingsDialog::Choice)
                saved == CUSTOM_SERVICES -> SettingsDialog.CustomServicesConsent
                saved == BACKGROUND_WORK -> SettingsDialog.BackgroundWorkHint
                saved == CALENDAR_ACCESS -> SettingsDialog.CalendarAccess(locked = false)
                saved == CALENDAR_ACCESS_LOCKED -> SettingsDialog.CalendarAccess(locked = true)
                else -> null
            },
        )

        val Saver: Saver<SettingsDialogState, String> = Saver(save = { it.save() }, restore = ::restore)
    }
}

@Composable
fun rememberSettingsDialogState(): SettingsDialogState =
    rememberSaveable(saver = SettingsDialogState.Saver) { SettingsDialogState() }

/**
 * The dialog of [dialogs] in the kit's dialog window. A choice reports [SettingsActions.onChoice] with the picked
 * option, the consent turns the services on through [SettingsActions.onToggle], and the background work and
 * calendar dialogs hand their button to the host. Back, a tap outside and the dismiss button only close.
 */
@Composable
internal fun SettingsDialogs(
    dialogs: SettingsDialogState,
    sections: List<SettingSection>,
    actions: SettingsActions,
    theme: ThemeSpec? = null,
) {
    val dialog = dialogs.dialog ?: return
    SettingsDialogBody(
        dialog = dialog,
        sections = sections,
        theme = theme,
        windowed = true,
        onConfirm = { action -> if (dialogs.close() != null) action() },
        onDismiss = { dialogs.close() },
        actions = actions,
    )
}

/**
 * [dialog] in the kit's window, or without it ([windowed] false) for previews. [onConfirm] closes the dialog and
 * runs the button's action if the dialog was still shown. [theme] is the page's appearance: the accent swatches show
 * its style and the custom colour.
 */
@Composable
internal fun SettingsDialogBody(
    dialog: SettingsDialog,
    sections: List<SettingSection>,
    windowed: Boolean,
    onConfirm: (action: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    actions: SettingsActions,
    theme: ThemeSpec? = null,
) {
    when (dialog) {
        is SettingsDialog.Choice -> {
            val item = sections.asSequence().flatMap { it.items }.filterIsInstance<SettingItem.Choice>()
                .firstOrNull { it.id == dialog.id } ?: return
            if (item.id == SettingRowId.ACCENT_COLOR) {
                // A colour applies on tap and the dialog stays, so the whole app behind it shows the pick at once.
                AccentChoice(windowed, item, theme ?: ThemeSpec(), actions, onDismiss)
            } else {
                SettingsChoice(windowed, item, onSelect = { key -> onConfirm { actions.onChoice(item.id, key) } }, onDismiss)
            }
        }
        SettingsDialog.CustomServicesConsent -> SettingsConfirm(
            windowed = windowed,
            title = stringResource(Res.string.settings_custom_services_consent_title),
            text = stringResource(Res.string.settings_custom_services_consent_message),
            confirmLabel = stringResource(Res.string.settings_custom_services_enable),
            dismissLabel = stringResource(CoreRes.string.common_cancel),
            onConfirm = { onConfirm { actions.onToggle(SettingRowId.CUSTOM_SERVICES, true) } },
            onDismiss = onDismiss,
        )
        SettingsDialog.BackgroundWorkHint -> SettingsConfirm(
            windowed = windowed,
            title = stringResource(Res.string.settings_background_work_title),
            text = stringResource(Res.string.background_work_dialog_message),
            confirmLabel = stringResource(Res.string.background_work_allow),
            dismissLabel = stringResource(Res.string.background_work_later),
            onConfirm = { onConfirm { actions.onAllowBackgroundWork() } },
            onDismiss = onDismiss,
        )
        // The Material 3 hero-icon dialog: the calendar in the secondary colour above a centred title.
        is SettingsDialog.CalendarAccess -> SettingsConfirm(
            windowed = windowed,
            title = stringResource(Res.string.calendar_access_title),
            text = stringResource(Res.string.calendar_access_rationale),
            confirmLabel = stringResource(
                if (dialog.locked) Res.string.calendar_access_open_settings else Res.string.calendar_access_allow,
            ),
            dismissLabel = stringResource(Res.string.calendar_access_later),
            onConfirm = {
                onConfirm { if (dialog.locked) actions.onOpenAppSettings() else actions.onAllowCalendarAccess() }
            },
            onDismiss = onDismiss,
            icon = painterResource(KitRes.drawable.ic_calendar_add),
        )
    }
}

/** The options as radio rows with the current one marked; none is marked while the value is unknown. */
@Composable
private fun SettingsChoice(windowed: Boolean, item: SettingItem.Choice, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val title = item.title.asString()
    val labels = item.options.map { it.label.asString() }
    val descriptions = item.options.map { it.description?.asString() }
    val selected = item.options.indexOfFirst { it.key == item.selectedOptionKey }
    val select = { index: Int -> onSelect(item.options[index].key) }
    val dismissLabel = stringResource(CoreRes.string.common_cancel)
    if (windowed) {
        ChoiceDialog(title, labels, selected, select, onDismiss, dismissLabel = dismissLabel, descriptions = descriptions)
    } else {
        ChoiceDialogSurface(title, labels, selected, select, onDismiss, dismissLabel = dismissLabel, descriptions = descriptions)
    }
}

/**
 * The options as colour swatches of their schemes in [theme]'s style; the done button closes. While «Свой цвет» is
 * picked, its picker sits under the swatches and reports the colour as the custom colour row's `#RRGGBB`.
 */
@Composable
private fun AccentChoice(
    windowed: Boolean,
    item: SettingItem.Choice,
    theme: ThemeSpec,
    actions: SettingsActions,
    onDismiss: () -> Unit,
) {
    val title = item.title.asString()
    val swatches = item.options.map { option ->
        accentSwatch(theme.copy(accent = AccentColor.valueOf(option.key)), option.label.asString())
    }
    val selected = item.options.indexOfFirst { it.key == item.selectedOptionKey }.takeIf { it >= 0 }
    val select = { index: Int -> actions.onChoice(item.id, item.options[index].key) }
    val done = stringResource(Res.string.settings_accent_color_done)
    val picker: (@Composable () -> Unit)? = if (item.selectedOptionKey == AccentColor.CUSTOM.name) {
        {
            ColorPicker(
                argb = theme.customArgb,
                onColorChange = { argb -> actions.onChoice(SettingRowId.ACCENT_CUSTOM, HexColor.format(argb)) },
                labels = colorPickerLabels(),
            )
        }
    } else {
        null
    }
    if (windowed) {
        SwatchChoiceDialog(title, swatches, selected, select, onDismiss, done, below = picker)
    } else {
        SwatchChoiceDialogSurface(title, swatches, selected, select, onDismiss, done, below = picker)
    }
}

@Composable
private fun colorPickerLabels() = ColorPickerLabels(
    hue = stringResource(Res.string.settings_color_picker_hue),
    saturation = stringResource(Res.string.settings_color_picker_saturation),
    brightness = stringResource(Res.string.settings_color_picker_brightness),
    hex = stringResource(Res.string.settings_color_picker_hex),
    hexError = stringResource(Res.string.settings_color_picker_hex_error),
    preview = stringResource(Res.string.settings_color_picker_preview),
)

@Composable
private fun SettingsConfirm(
    windowed: Boolean,
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: Painter? = null,
) {
    if (windowed) {
        ConfirmDialog(title, confirmLabel, dismissLabel, onConfirm, onDismiss, text = text, icon = icon)
    } else {
        ConfirmDialogSurface(title, confirmLabel, dismissLabel, onConfirm, onDismiss, text = text, icon = icon)
    }
}
