package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.ProvideItmoExpressive

/** The switch off: enabled and disabled, the menu closed. */
@Preview
@Composable
private fun ItmoSplitButtonStandardPreview() = ItmoPreview { SplitButtons() }

/** The switch on. */
@Preview
@Composable
private fun ItmoSplitButtonExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { SplitButtons() }
}

@Composable
private fun SplitButtons() {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        listOf(true, false).forEach { enabled ->
            ItmoSplitButton(
                label = "Экспорт в календарь",
                onClick = {},
                menuLabel = "Выбрать период",
                options = listOf(
                    ItmoSplitButtonOption("Эта неделя", onClick = {}),
                    ItmoSplitButtonOption("Четыре недели", onClick = {}),
                ),
                menuExpanded = false,
                onMenuExpandedChange = {},
                enabled = enabled,
            )
        }
    }
}
