package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.designsystem.components.dialogs.SwatchChoiceDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsChoiceRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsToggleRow
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.LocalPreviewAppearance

/** The names the settings page gives the presets, in [AccentColor] order. */
private val AccentNames = listOf(
    "Как обои", "Фирменный", "Бирюзовый", "Зелёный", "Янтарный", "Красный", "Розовый", "Фиолетовый",
)

/** The accent colour picker with every preset, a preset picked; its own scheme is the appearance's. */
@Preview
@Composable
private fun AccentPickerPreview() = ItmoPreview {
    val dark = LocalPreviewAppearance.current.dark || isSystemInDarkTheme()
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        SwatchChoiceDialogSurface(
            title = "Цвет оформления",
            swatches = AccentColor.entries.mapIndexed { index, accent -> accentSwatch(accent, AccentNames[index], dark) },
            selectedIndex = AccentColor.TEAL.ordinal,
            onSelect = {},
            onDismiss = {},
            confirmLabel = "Готово",
        )
    }
}

/** One sample screen per seed: the brand scheme and three presets, so a recipe change shows across hues. */
@Preview(heightDp = 1400)
@Composable
private fun AccentSamplesPreview() = Column {
    listOf(AccentColor.BRAND, AccentColor.TEAL, AccentColor.AMBER, AccentColor.PURPLE).forEach { accent ->
        ItmoPreview(colorSource = accent.colorSource) { AccentSample(AccentNames[accent.ordinal]) }
    }
}

@Composable
private fun AccentSample(name: String) {
    Column(
        Modifier.padding(ItmoTheme.spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
    ) {
        Text(name, color = ItmoTheme.colorScheme.onSurface, style = ItmoTheme.typography.titleLarge)
        SettingsGroup {
            row("toggle") { SettingsToggleRow("Изменения в расписании", checked = true, onCheckedChange = {}) }
            row("choice") { SettingsChoiceRow("Цвет оформления", value = name, onClick = {}) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact)) {
            Button(onClick = {}) { Text("Записаться") }
            FilledTonalButton(onClick = {}) { Text("Отмена") }
        }
    }
}
