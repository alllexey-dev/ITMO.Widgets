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

/** The switch off: segmented buttons, two and four options, the long label cut at 1.3 in 320 dp. */
@Preview
@Composable
private fun ItmoButtonGroupStandardPreview() = ItmoPreview { Groups() }

/** The switch on: the connected group of toggle buttons. */
@Preview
@Composable
private fun ItmoButtonGroupExpressivePreview() = ItmoPreview {
    ProvideItmoExpressive(expressive = true) { Groups() }
}

@Composable
private fun Groups() {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        ItmoButtonGroup(options = listOf("Друзья", "Все"), selectedIndex = 0, onSelect = {})
        ItmoButtonGroup(
            options = listOf("Записи", "Баллы", "Посещения", "Нормативы"),
            selectedIndex = 2,
            onSelect = {},
        )
    }
}
