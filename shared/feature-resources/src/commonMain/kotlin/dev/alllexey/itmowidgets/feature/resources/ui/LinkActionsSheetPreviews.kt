package dev.alllexey.itmowidgets.feature.resources.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/*
 * LX-1c recorded the XML references as `LinkActionsSheetContent_<state>`, so each state is a function called
 * `LinkActionsSheetContent` in a holder class of its own. The sheet wraps its content on the sheet's colour, as
 * `LinkActionsBottomSheet` opens it, over the links of `SubjectLinksSamples`.
 */

/** Another student's Google Sheet: the pill with the own vote, the author, `Мои баллы` and `Пожаловаться`. */
internal class LinkActionsSheetOthersSheetPreview {
    @Preview(name = "others-sheet")
    @Composable
    fun LinkActionsSheetContent() = ActionsPreview("others-sheet")
}

/** An own flow link: its score without arrows, then pin, edit and delete. */
internal class LinkActionsSheetOwnSharedPreview {
    @Preview(name = "own-shared")
    @Composable
    fun LinkActionsSheetContent() = ActionsPreview("own-scores")
}

/** An own private link has no score. */
internal class LinkActionsSheetOwnPrivatePreview {
    @Preview(name = "own-private")
    @Composable
    fun LinkActionsSheetContent() = ActionsPreview("own-other")
}

/** An own rejected link with a long title: the review state in the caption and the moderator's reason under it. */
internal class LinkActionsSheetOwnRejectedPreview {
    @Preview(name = "own-rejected")
    @Composable
    fun LinkActionsSheetContent() = ActionsPreview("own-rejected")
}

/** A vote restriction: another student's link keeps its score without arrows. */
internal class LinkActionsSheetRestrictedPreview {
    @Preview(name = "restricted")
    @Composable
    fun LinkActionsSheetContent() = ActionsPreview("tasks-flow", restricted = true)
}

@Composable
private fun ActionsPreview(linkId: String, restricted: Boolean = false) = ItmoPreview {
    val shape = ItmoTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow),
    ) {
        LinkActionsSheetContent(
            if (restricted) SubjectLinksSamples.restricted else SubjectLinksSamples.content,
            linkId,
            LinkActionsActions(),
            Modifier.fillMaxWidth(),
        )
    }
}
