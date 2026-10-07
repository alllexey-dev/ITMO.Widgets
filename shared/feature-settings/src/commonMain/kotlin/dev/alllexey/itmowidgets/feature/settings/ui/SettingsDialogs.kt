package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ChoiceDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingRowId
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingSection
import dev.alllexey.itmowidgets.shared.core.common_cancel
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_add
import dev.alllexey.itmowidgets.shared.feature.settings.Res
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/** A dialog of a settings page. Each one is saved by [SettingsDialogState], so it comes back after recreation. */
sealed interface SettingsDialog {

    /**
     * The single choice of the [SettingItem.Choice] row [id]: text size, the QR animation, the privacy audiences. It
     * shows the row as the page has it now and is not drawn while the page has no such row.
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
internal fun SettingsDialogs(dialogs: SettingsDialogState, sections: List<SettingSection>, actions: SettingsActions) {
    val dialog = dialogs.dialog ?: return
    SettingsDialogBody(
        dialog = dialog,
        sections = sections,
        windowed = true,
        onConfirm = { action -> if (dialogs.close() != null) action() },
        onDismiss = { dialogs.close() },
        actions = actions,
    )
}

/**
 * [dialog] in the kit's window, or without it ([windowed] false) for previews. [onConfirm] closes the dialog and
 * runs the button's action if the dialog was still shown.
 */
@Composable
internal fun SettingsDialogBody(
    dialog: SettingsDialog,
    sections: List<SettingSection>,
    windowed: Boolean,
    onConfirm: (action: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    actions: SettingsActions,
) {
    when (dialog) {
        is SettingsDialog.Choice -> {
            val item = sections.asSequence().flatMap { it.items }.filterIsInstance<SettingItem.Choice>()
                .firstOrNull { it.id == dialog.id } ?: return
            SettingsChoice(windowed, item, onSelect = { key -> onConfirm { actions.onChoice(item.id, key) } }, onDismiss)
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
    val selected = item.options.indexOfFirst { it.key == item.selectedOptionKey }
    val select = { index: Int -> onSelect(item.options[index].key) }
    val dismissLabel = stringResource(CoreRes.string.common_cancel)
    if (windowed) {
        ChoiceDialog(title, labels, selected, select, onDismiss, dismissLabel = dismissLabel)
    } else {
        ChoiceDialogSurface(title, labels, selected, select, onDismiss, dismissLabel = dismissLabel)
    }
}

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
