package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes
import dev.alllexey.itmowidgets.shared.designsystem.ic_calendar_add
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.calendar_access_allow
import dev.alllexey.itmowidgets.shared.compose.strings.calendar_access_later
import dev.alllexey.itmowidgets.shared.compose.strings.calendar_access_open_settings
import dev.alllexey.itmowidgets.shared.compose.strings.calendar_access_rationale
import dev.alllexey.itmowidgets.shared.compose.strings.calendar_access_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** A dialog of a settings page. Each one is saved by [SettingsDialogState], so it comes back after recreation. */
sealed interface SettingsDialog {

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
        is SettingsDialog.CalendarAccess -> if (shown.locked) CALENDAR_ACCESS_LOCKED else CALENDAR_ACCESS
    }

    companion object {
        private const val CALENDAR_ACCESS = "calendar-access"
        private const val CALENDAR_ACCESS_LOCKED = "calendar-access-locked"

        /** The state [save] wrote; an unknown or missing value shows nothing. */
        fun restore(saved: String?): SettingsDialogState = SettingsDialogState(
            when {
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

/** The dialog of [dialogs] in the kit's window: its button goes to the host; back, a tap outside and «Не сейчас» close. */
@Composable
internal fun SettingsDialogs(dialogs: SettingsDialogState, actions: SettingsActions) {
    val dialog = dialogs.dialog ?: return
    SettingsDialogBody(
        dialog = dialog,
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
    windowed: Boolean,
    onConfirm: (action: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    actions: SettingsActions,
) {
    when (dialog) {
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
