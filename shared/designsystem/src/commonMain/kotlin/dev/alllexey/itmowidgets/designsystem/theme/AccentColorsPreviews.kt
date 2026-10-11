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
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.designsystem.components.dialogs.SwatchChoiceDialogSurface
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsChoiceRow
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsGroup
import dev.alllexey.itmowidgets.designsystem.components.settings.SettingsToggleRow
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.preview.LocalPreviewAppearance

/** The names the settings page gives the presets, in [AccentColor] order. */
private val AccentNames = listOf(
    "Как обои", "Фирменный", "Бирюзовый", "Зелёный", "Янтарный", "Красный", "Розовый", "Фиолетовый", "Свой цвет",
)

/** The accent colour picker with every preset, a preset picked; its own scheme is the appearance's. */
@Preview
@Composable
private fun AccentPickerPreview() = ItmoPreview {
    val dark = LocalPreviewAppearance.current.dark || isSystemInDarkTheme()
    Box(Modifier.padding(ItmoTheme.spacing.section)) {
        SwatchChoiceDialogSurface(
            title = "Цвет оформления",
            swatches = AccentColor.entries.mapIndexed { index, accent ->
                accentSwatch(ThemeSpec(accent = accent, customArgb = CUSTOM_SAMPLE), AccentNames[index], dark)
            },
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
        ItmoPreview(colorSource = ColorSource.Theme(ThemeSpec(accent))) { AccentSample(AccentNames[accent.ordinal]) }
    }
}

/** The sample in every palette style, from the teal preset: how far each style moves from the seed. */
@Preview(heightDp = 2400)
@Composable
private fun ThemeStylesPreview() = Column {
    ThemeStyle.entries.forEachIndexed { index, style ->
        ItmoPreview(colorSource = ColorSource.Theme(ThemeSpec(AccentColor.TEAL, style = style))) {
            AccentSample(StyleNames[index])
        }
    }
}

/**
 * The sample at the three contrast levels and with the black background, from a custom colour: in a light appearance
 * the black background shows nothing, in a dark one its containers stay apart from the page.
 */
@Preview(heightDp = 1400)
@Composable
private fun ThemeContrastPreview() = Column {
    val custom = ThemeSpec(AccentColor.CUSTOM, customArgb = CUSTOM_SAMPLE)
    ThemeContrast.entries.forEachIndexed { index, contrast ->
        ItmoPreview(colorSource = ColorSource.Theme(custom.copy(contrast = contrast))) { AccentSample(ContrastNames[index]) }
    }
    ItmoPreview(colorSource = ColorSource.Theme(custom.copy(pureBlack = true))) { AccentSample("Чёрный фон") }
}

private val StyleNames = listOf("Спокойная", "Яркая", "Выразительная", "Точная", "Насыщенная", "Нейтральная", "Монохром")

private val ContrastNames = listOf("Обычный", "Средний", "Высокий")

/** A custom seed far from every preset. */
private const val CUSTOM_SAMPLE: Int = 0xFF5C6BC0.toInt()

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
