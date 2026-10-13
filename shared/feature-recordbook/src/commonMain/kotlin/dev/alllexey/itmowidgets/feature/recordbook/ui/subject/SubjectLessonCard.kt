package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.kit.LessonCard
import dev.alllexey.itmowidgets.designsystem.kit.toLessonView
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonItem

/** A lesson ahead under its day, the schedule's lesson card; a tap opens the lesson sheet. */
@Composable
internal fun SubjectLessonCard(item: SubjectLessonItem, onOpen: (LessonTarget) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        Text(
            item.day.asString(),
            Modifier.padding(start = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.labelLarge,
        )
        LessonCard(
            item.lesson.toLessonView(),
            Modifier.clip(ItmoTheme.shapes.cardContent).clickable { onOpen(item.lesson.target) },
        )
    }
}
