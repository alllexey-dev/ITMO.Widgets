package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The profile's request answer: side by side at 1.0, stacked at 1.3 in 320 dp. */
@Preview
@Composable
private fun ButtonRowPreview() = ItmoPreview {
    ButtonRow(Modifier.padding(ItmoTheme.spacing.cardPadding)) {
        ProgressButton("Принять заявку", onClick = {})
        ProgressButton("Отклонить", onClick = {}, style = ProgressButtonStyle.Tonal)
    }
}

@Preview
@Composable
private fun ButtonRowSinglePreview() = ItmoPreview {
    ButtonRow(Modifier.padding(ItmoTheme.spacing.cardPadding)) {
        ProgressButton("Добавить в друзья", onClick = {})
    }
}

@Preview
@Composable
private fun ButtonRowInProgressPreview() = ItmoPreview {
    ButtonRow(Modifier.padding(ItmoTheme.spacing.cardPadding)) {
        ProgressButton("Принять заявку", onClick = {}, inProgress = true)
        ProgressButton("Отклонить", onClick = {}, style = ProgressButtonStyle.Tonal)
    }
}
