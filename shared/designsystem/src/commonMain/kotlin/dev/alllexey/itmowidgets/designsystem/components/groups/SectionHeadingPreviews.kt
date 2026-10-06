package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

@Preview
@Composable
private fun SectionHeadingPreview() = ItmoPreview {
    Column(Modifier.groupPreviewBackdrop().padding(horizontal = ItmoTheme.spacing.screenMargin)) {
        SectionHeading("Пары", spacing = SectionHeadingSpacing.First)
        SectionHeading("Преподаватели")
        SectionHeading(PreviewFixtures.LongSubjectName, spacing = SectionHeadingSpacing.Sheet)
    }
}

@Preview
@Composable
private fun SectionHeadingSubheadingPreview() = ItmoPreview {
    Column(Modifier.groupPreviewBackdrop().padding(horizontal = ItmoTheme.spacing.screenMargin)) {
        SectionSubheading("Лабораторные", value = "30 / 48")
        SectionSubheading(
            PreviewFixtures.LongSubjectName,
            value = "12,5 / 20",
            supporting = {
                Text(
                    "Ниже минимума",
                    color = ItmoTheme.colorScheme.error,
                    style = ItmoTheme.typography.labelSmall,
                )
            },
        )
        SectionSubheading("Тесты")
    }
}
