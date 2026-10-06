package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoSwitch
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.ConfirmDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.dialogs.InfoDialog
import dev.alllexey.itmowidgets.designsystem.components.dialogs.InfoDialogSurface
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignEvent
import dev.alllexey.itmowidgets.shared.core.common_back
import dev.alllexey.itmowidgets.shared.core.common_got_it
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.common_ok
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_unsubscribe
import dev.alllexey.itmowidgets.shared.feature.sport.sport_free_sign_switch
import dev.alllexey.itmowidgets.shared.feature.sport.sport_link_unavailable_text
import dev.alllexey.itmowidgets.shared.feature.sport.sport_link_unavailable_title
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

/** A dialog of the `Запись` screen, opened by a [SportSignEvent]. */
sealed interface SportSignDialog {

    /** Joining the auto-sign queue; [showForceSign] adds today's switch that relaxes the one-hour deadline. */
    data class AutoSignConfirm(
        val title: UiText,
        val message: UiText,
        val showForceSign: Boolean,
        val command: SportSignCommand,
    ) : SportSignDialog

    /** Leaving the auto-sign queue. */
    data class AutoSignDelete(val message: UiText, val command: SportSignCommand) : SportSignDialog

    data class Info(val title: UiText?, val message: UiText) : SportSignDialog

    /** A shared lesson that has passed or is not in the catalog: `Занятие недоступно`. */
    data object LinkUnavailable : SportSignDialog

    companion object {
        /** The dialog [event] opens; null for the events the screen handles otherwise. */
        fun of(event: SportSignEvent): SportSignDialog? = when (event) {
            is SportSignEvent.ShowAutoSignConfirmDialog ->
                AutoSignConfirm(event.title, event.message, event.showForceSignButton, event.command)
            is SportSignEvent.ShowAutoSignDeleteDialog -> AutoSignDelete(event.message, event.command)
            is SportSignEvent.ShowInfoDialog -> Info(event.title, event.message)
            SportSignEvent.ShowLinkUnavailable -> LinkUnavailable
            is SportSignEvent.ShowToast, is SportSignEvent.ShowError, is SportSignEvent.OpenLessonDetails -> null
        }
    }
}

/**
 * The dialog on the `Запись` screen, one at a time: a newer event replaces it, as the screen answers the latest tap.
 * It lives in composition like the View dialogs it replaces, so recreation closes it and never re-runs a command.
 */
@Stable
class SportSignDialogState {

    var dialog: SportSignDialog? by mutableStateOf(null)
        private set

    /** Opens the dialog of [event]; false when [event] is not a dialog. */
    fun show(event: SportSignEvent): Boolean {
        val next = SportSignDialog.of(event) ?: return false
        dialog = next
        return true
    }

    fun dismiss() {
        dialog = null
    }

    /**
     * Closes the dialog and hands out its command, once: a second tap on the button before the window goes gets
     * null. Dialogs without a command only close.
     */
    fun confirm(): SportSignCommand? {
        val shown = dialog ?: return null
        dialog = null
        return when (shown) {
            is SportSignDialog.AutoSignConfirm -> shown.command
            is SportSignDialog.AutoSignDelete -> shown.command
            is SportSignDialog.Info, SportSignDialog.LinkUnavailable -> null
        }
    }
}

@Composable
fun rememberSportSignDialogState(): SportSignDialogState = remember { SportSignDialogState() }

/**
 * The dialog of [state] in the kit's dialog window. A confirm closes it and runs [onExecute] with its command and the
 * force-sign switch (off when the dialog has none) exactly once; back, a tap outside and `Назад` run nothing.
 */
@Composable
fun SportSignDialogs(state: SportSignDialogState, onExecute: (command: SportSignCommand, forceSign: Boolean) -> Unit) {
    val dialog = state.dialog ?: return
    SportSignDialogBody(
        dialog = dialog,
        windowed = true,
        onConfirm = { forceSign -> state.confirm()?.let { onExecute(it, forceSign) } },
        onDismiss = state::dismiss,
    )
}

/**
 * A success message of the screen ([SportSignEvent.ShowToast]) as a snackbar of the screen's host: commonMain has no
 * `Toast` (report 02 A2-CO10). The text and the short duration stay.
 */
suspend fun SnackbarHostState.showSportSignMessage(event: SportSignEvent.ShowToast) {
    showSnackbar(event.message.resolve())
}

/** Test tags of the sport sign dialogs. */
object SportSignDialogsTestTags {
    const val FORCE_SIGN = "sport_sign_dialog_force_sign"
}

/**
 * [dialog] in the kit's window, or without it ([windowed] false) for previews. [onConfirm] gets the force-sign
 * switch; the info dialogs' one button dismisses.
 */
@Composable
internal fun SportSignDialogBody(
    dialog: SportSignDialog,
    windowed: Boolean,
    onConfirm: (forceSign: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is SportSignDialog.AutoSignConfirm -> {
            var forceSign by remember(dialog) { mutableStateOf(false) }
            SportConfirm(
                windowed = windowed,
                title = dialog.title.asString(),
                text = dialog.message.asString(),
                confirmLabel = stringResource(Res.string.sport_auto_sign_title),
                onConfirm = { onConfirm(dialog.showForceSign && forceSign) },
                onDismiss = onDismiss,
                content = if (dialog.showForceSign) {
                    { ForceSignSwitch(forceSign, onChange = { forceSign = it }) }
                } else {
                    null
                },
            )
        }
        is SportSignDialog.AutoSignDelete -> SportConfirm(
            windowed = windowed,
            title = null,
            text = dialog.message.asString(),
            confirmLabel = stringResource(Res.string.sport_auto_sign_unsubscribe),
            onConfirm = { onConfirm(false) },
            onDismiss = onDismiss,
        )
        is SportSignDialog.Info -> SportInfo(
            windowed = windowed,
            title = dialog.title?.asString(),
            text = dialog.message.asString(),
            buttonLabel = stringResource(Res.string.common_ok),
            onDismiss = onDismiss,
        )
        SportSignDialog.LinkUnavailable -> SportInfo(
            windowed = windowed,
            title = stringResource(Res.string.sport_link_unavailable_title),
            text = stringResource(Res.string.sport_link_unavailable_text),
            buttonLabel = stringResource(CoreRes.string.common_got_it),
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun SportConfirm(
    windowed: Boolean,
    title: String?,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val dismissLabel = stringResource(CoreRes.string.common_back)
    if (windowed) {
        ConfirmDialog(title, confirmLabel, dismissLabel, onConfirm, onDismiss, text = text, content = content)
    } else {
        ConfirmDialogSurface(title, confirmLabel, dismissLabel, onConfirm, onDismiss, text = text, content = content)
    }
}

@Composable
private fun SportInfo(windowed: Boolean, title: String?, text: String, buttonLabel: String, onDismiss: () -> Unit) {
    if (windowed) {
        InfoDialog(title, text, buttonLabel, onDismiss)
    } else {
        InfoDialogSurface(title, text, buttonLabel, onDismiss)
    }
}

/** `dialog_free_sign`: the label, then the switch; the whole row toggles. */
@Composable
private fun ForceSignSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = ItmoTheme.spacing.group)
            .heightIn(min = ItmoTheme.spacing.touchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .testTag(SportSignDialogsTestTags.FORCE_SIGN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.sport_free_sign_switch),
            Modifier
                .weight(1f)
                .padding(end = ItmoTheme.spacing.content),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.bodySmall,
        )
        ItmoSwitch(checked = checked, onCheckedChange = null)
    }
}
