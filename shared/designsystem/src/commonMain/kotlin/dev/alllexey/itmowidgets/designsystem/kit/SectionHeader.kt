package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The title of a section (`Пары`, `Нужно сделать`, `Новое`) with an optional neutral [count] about it (`5 пар`) at the
 * end, never glued to the title and never in an accent colour.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, count: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = ItmoTheme.spacing.related, end = ItmoTheme.spacing.related, top = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Text(
            title,
            Modifier.weight(1f).semantics { heading() },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
        )
        if (count != null) {
            Text(count, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.labelLarge)
        }
    }
}
