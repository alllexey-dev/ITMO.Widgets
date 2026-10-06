package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.components.groups.PreviewBackdrop
import dev.alllexey.itmowidgets.designsystem.components.groups.groupPreviewBackdrop
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** No vote, an up vote, a down vote below zero, and a negative score the viewer has not voted on. */
@Preview
@Composable
private fun VotePillPreview() = ItmoPreview {
    PillColumn {
        VotePill(12, myVote = null, "Рейтинг 12", onVote = {}, PreviewLabels)
        VotePill(13, myVote = Vote.Up, "Рейтинг 13", onVote = {}, PreviewLabels)
        VotePill(-2, myVote = Vote.Down, "Рейтинг -2", onVote = {}, PreviewLabels)
        VotePill(-5, myVote = null, "Рейтинг -5", onVote = {}, PreviewLabels)
    }
}

/** The score alone (own link, no right to vote) and a vote in flight, which keeps its look. */
@Preview
@Composable
private fun VotePillReadOnlyPreview() = ItmoPreview {
    PillColumn {
        VotePill(7, myVote = null, "Рейтинг 7", onVote = null, PreviewLabels)
        VotePill(128, myVote = null, "Рейтинг 128", onVote = null, PreviewLabels)
        VotePill(4, myVote = Vote.Up, "Рейтинг 4", onVote = {}, PreviewLabels, enabled = false)
    }
}

@Composable
private fun PillColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.groupPreviewBackdrop(PreviewBackdrop.Cell).padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) { content() }
}

private val PreviewLabels = VoteLabels(up = "Полезная ссылка", down = "Бесполезная ссылка")
