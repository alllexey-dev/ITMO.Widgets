package dev.alllexey.itmowidgets.feature.sport.ui.sign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_existing_day
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_existing_entry
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_free_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_future_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_title

/** A free lesson starting today: the queue offer with the force-sign switch. */
@Preview
@Composable
private fun SportSignDialogsForceSignPreview() = DialogPreview(
    SportSignDialog.AutoSignConfirm(
        title = UiText.Res(Res.string.sport_auto_sign_title),
        message = UiText.Res(Res.string.sport_auto_sign_free_description),
        showForceSign = true,
        command = SportSignCommand.CreateFreeSign(1),
    ),
)

/** A lesson whose booking has not opened yet: the queue offer without the switch. */
@Preview
@Composable
private fun SportSignDialogsAutoSignPreview() = DialogPreview(
    SportSignDialog.AutoSignConfirm(
        title = UiText.Res(Res.string.sport_auto_sign_title),
        message = UiText.Res(Res.string.sport_auto_sign_future_description),
        showForceSign = false,
        command = SportSignCommand.CreateAutoSign(1),
    ),
)

@Preview
@Composable
private fun SportSignDialogsDeletePreview() = DialogPreview(
    SportSignDialog.AutoSignDelete(
        message = UiText.Res(Res.string.sport_auto_sign_existing_entry, listOf(2, 5)),
        command = SportSignCommand.CancelAutoSign(1),
    ),
)

/** The info dialog with the longest message: a taken day with a long section and teacher. */
@Preview
@Composable
private fun SportSignDialogsInfoPreview() = DialogPreview(
    SportSignDialog.Info(
        title = null,
        message = UiText.Res(
            Res.string.sport_auto_sign_existing_day,
            listOf("Фитнес-аэробика и функциональный тренинг", PreviewFixtures.LongPersonName),
        ),
    ),
)

@Preview
@Composable
private fun SportSignDialogsLinkUnavailablePreview() = DialogPreview(SportSignDialog.LinkUnavailable)

/** The dialog on the screen margin, as the window centres it. */
@Composable
private fun DialogPreview(dialog: SportSignDialog) = ItmoPreview {
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        SportSignDialogBody(dialog, windowed = false, onConfirm = {}, onDismiss = {})
    }
}
