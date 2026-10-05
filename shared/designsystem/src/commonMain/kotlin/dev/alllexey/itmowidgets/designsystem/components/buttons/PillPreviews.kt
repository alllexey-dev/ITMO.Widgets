package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

@Preview
@Composable
private fun PillPreview() = ItmoPreview {
    Row(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill("моя")
        Pill("12")
    }
}

@Preview
@Composable
private fun PillStatusPreview() = ItmoPreview {
    Row(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill("На проверке", tone = ItmoTheme.colorScheme.tertiary)
        Pill("Отклонён", tone = ItmoTheme.colorScheme.error)
        Pill("Скрыт", tone = ItmoTheme.colorScheme.error)
    }
}
