package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonItem

/** A lesson ahead under its day. */
@Composable
internal fun SubjectLessonCard(item: SubjectLessonItem) {
    Column(verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related)) {
        Text(
            item.day.asString(),
            Modifier.padding(start = ItmoTheme.spacing.related),
            color = ItmoTheme.colorScheme.onSurfaceVariant,
            style = ItmoTheme.typography.labelLarge,
        )
        Text(item.lesson.subjectName)
    }
}
