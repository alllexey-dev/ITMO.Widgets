package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_link
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
private fun ConnectedGroupPreview() = ItmoPreview {
    Column(Modifier.padding(horizontal = ItmoTheme.spacing.screenMargin).padding(bottom = ItmoTheme.spacing.group)) {
        SectionHeading("Ссылки", spacing = SectionHeadingSpacing.First)
        PreviewLinks(GroupSurface.Screen)
    }
}

@Preview
@Composable
private fun ConnectedGroupSheetPreview() = ItmoPreview {
    Column(
        Modifier
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.group),
    ) {
        SectionHeading("Ссылки", spacing = SectionHeadingSpacing.Sheet)
        PreviewLinks(GroupSurface.Sheet)
    }
}

@Preview
@Composable
private fun ConnectedGroupSinglePreview() = ItmoPreview {
    Column(Modifier.padding(ItmoTheme.spacing.screenMargin)) {
        PreviewRow(PreviewFixtures.LongSubjectName, "Экзамен, 5 ECTS", GroupPosition.Single, GroupSurface.Screen)
        Text(
            "Группа сразу после группы без заголовка",
            Modifier.padding(top = ItmoTheme.spacing.content, start = ItmoTheme.spacing.cardPadding),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PreviewLinks(surface: GroupSurface) {
    PreviewRow("Курс на Moodle", "moodle.itmo.ru", GroupPosition.First, surface)
    PreviewRow(PreviewFixtures.LongSubjectName, "Записи лекций", GroupPosition.Middle, surface)
    PreviewRow("Чат потока", "t.me", GroupPosition.Middle, surface)
    GroupActionRow(
        "Все ссылки, 5",
        painterResource(Res.drawable.ic_link),
        onClick = {},
        modifier = Modifier.connectedGroupItem(GroupPosition.Last, surface),
    )
}

@Composable
private fun PreviewRow(title: String, caption: String, position: GroupPosition, surface: GroupSurface) {
    Column(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(position, surface)
            .clickable {}
            .heightIn(min = 56.dp)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.content),
    ) {
        Text(title, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.bodyLarge)
        Text(caption, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
    }
}
