package dev.alllexey.itmowidgets.designsystem.components.rows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupSurface
import dev.alllexey.itmowidgets.designsystem.components.groups.PreviewBackdrop
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.components.groups.groupPreviewBackdrop
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_chat
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import dev.alllexey.itmowidgets.shared.designsystem.ic_menu_book
import dev.alllexey.itmowidgets.shared.designsystem.ic_table
import org.jetbrains.compose.resources.painterResource

/** One connected group: another student's link with votes, an own link, a chat without a trailing element. */
@Preview
@Composable
private fun LinkRowPreview() = ItmoPreview {
    Column(Modifier.groupPreviewBackdrop().padding(ItmoTheme.spacing.screenMargin)) {
        LinkRow(
            "Баллы потока",
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.First),
            icon = painterResource(Res.drawable.ic_table),
            caption = "docs.google.com",
            votes = { VotePill(12, myVote = Vote.Up, "Рейтинг 12", onVote = {}, PreviewLabels) },
        )
        LinkRow(
            "Конспекты лекций",
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.Middle),
            icon = painterResource(Res.drawable.ic_menu_book),
            caption = "Видна группе P3119",
            ownBadge = "моя",
        )
        LinkRow(
            "Чат потока",
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.Last),
            icon = painterResource(Res.drawable.ic_chat),
            caption = "t.me",
        )
    }
}

/** Long titles wrap beside the badge and the pill; a row without an icon, inside a sheet. */
@Preview
@Composable
private fun LinkRowLongTitlePreview() = ItmoPreview {
    Column(Modifier.groupPreviewBackdrop(PreviewBackdrop.Sheet).padding(ItmoTheme.spacing.screenMargin)) {
        LinkRow(
            PreviewFixtures.LongSubjectName,
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.First, GroupSurface.Sheet),
            icon = painterResource(Res.drawable.ic_link),
            caption = "very-long-domain-name.example.invalid",
            votes = { VotePill(-3, myVote = null, "Рейтинг -3", onVote = {}, PreviewLabels) },
        )
        LinkRow(
            PreviewFixtures.LongSubjectName,
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.Middle, GroupSurface.Sheet),
            ownBadge = "моя",
        )
        LinkRow(
            PreviewFixtures.LongSubjectName,
            onClick = {},
            modifier = Modifier.connectedGroupItem(GroupPosition.Last, GroupSurface.Sheet),
            caption = PreviewFixtures.LongPersonName,
            votes = { VotePill(0, myVote = null, "Рейтинг 0", onVote = null, PreviewLabels) },
        )
    }
}

private val PreviewLabels = VoteLabels(up = "Полезная ссылка", down = "Бесполезная ссылка")
