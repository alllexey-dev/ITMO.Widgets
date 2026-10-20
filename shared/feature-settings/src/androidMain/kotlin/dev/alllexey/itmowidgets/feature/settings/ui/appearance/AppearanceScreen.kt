package dev.alllexey.itmowidgets.feature.settings.ui.appearance

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.controls.ColorPicker
import dev.alllexey.itmowidgets.designsystem.components.controls.ColorPickerLabels
import dev.alllexey.itmowidgets.designsystem.kit.ItmoDetailScreen
import dev.alllexey.itmowidgets.designsystem.kit.ListGroup
import dev.alllexey.itmowidgets.designsystem.kit.ListGroupNote
import dev.alllexey.itmowidgets.designsystem.kit.SectionHeader
import dev.alllexey.itmowidgets.designsystem.kit.SegmentedChoice
import dev.alllexey.itmowidgets.designsystem.kit.SwitchListRow
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.accentSwatch
import dev.alllexey.itmowidgets.designsystem.theme.colorSchemeOf
import dev.alllexey.itmowidgets.feature.settings.presentation.appearance.AppearanceUiState
import dev.alllexey.itmowidgets.feature.settings.presentation.appearance.StyleOption
import dev.alllexey.itmowidgets.feature.settings.ui.AppearanceSample
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import org.jetbrains.compose.resources.painterResource

/** What «Оформление» asks of its host; every choice goes to `AppearanceViewModel` and applies at once. */
data class AppearanceActions(
    val onBack: () -> Unit = {},
    val onAccent: (AccentColor) -> Unit = {},
    val onCustomColor: (Int) -> Unit = {},
    val onStyle: (ThemeStyle) -> Unit = {},
    val onContrast: (ThemeContrast) -> Unit = {},
    val onBlackBackground: (Boolean) -> Unit = {},
)

/** Test tags of «Оформление». */
object AppearanceTags {
    const val SCREEN = "appearance"
    const val SAMPLE = "appearance_sample"
    const val PICKER = "appearance_picker"
    const val BLACK = "appearance_black"
    fun accent(accent: AccentColor) = "appearance_accent_${accent.name.lowercase()}"
    fun style(style: ThemeStyle) = "appearance_style_${style.name.lowercase()}"
    fun contrast(contrast: ThemeContrast) = "appearance_contrast_${contrast.name.lowercase()}"
}

/**
 * «Оформление» (docs/settings.md, Appearance): the sample in the light and the dark theme on top, then one block per
 * choice, each visible at once: the colour as named swatches (the picker under them while `Свой цвет` is picked), the
 * palette as small samples with what the picked one does, the contrast as a segmented choice and the black background.
 * A line says what a choice does where it is not obvious: `Как обои`, or why the colours are off under `Монохром`.
 */
@Composable
fun AppearanceScreen(
    state: AppearanceUiState,
    actions: AppearanceActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    ItmoDetailScreen(
        title = text(SettingsStrings.settings_appearance_title),
        onBack = actions.onBack,
        backLabel = text(CoreStrings.settings_back),
        modifier = modifier.testTag(AppearanceTags.SCREEN),
    ) { padding ->
        if (!state.loaded) return@ItmoDetailScreen
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = ItmoTheme.spacing.screenMargin)
                .padding(top = ItmoTheme.spacing.related, bottom = ItmoTheme.spacing.group)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.content),
        ) {
            AppearanceSample(state.theme, Modifier.testTag(AppearanceTags.SAMPLE))

            SectionHeader(text(SettingsStrings.settings_appearance_color))
            ListGroup {
                item { Swatches(state, actions) }
                state.customArgb?.let { argb ->
                    item {
                        ColorPicker(
                            argb = argb,
                            onColorChange = actions.onCustomColor,
                            labels = colorPickerLabels(),
                            modifier = Modifier.padding(ItmoTheme.spacing.cardPadding).testTag(AppearanceTags.PICKER),
                        )
                    }
                }
            }
            state.accentNote?.let { ListGroupNote(it.asString()) }

            SectionHeader(text(SettingsStrings.settings_appearance_palette))
            Palettes(state, actions)
            state.style?.let { ListGroupNote(it.description.asString()) }

            SectionHeader(text(SettingsStrings.settings_theme_contrast_title))
            SegmentedChoice(
                options = state.contrasts.map { it.label.asString() },
                selectedIndex = state.contrasts.indexOfFirst { it.contrast == state.theme.contrast }.coerceAtLeast(0),
                onSelect = { actions.onContrast(state.contrasts[it].contrast) },
                testTags = state.contrasts.map { AppearanceTags.contrast(it.contrast) },
            )
            ListGroupNote(text(SettingsStrings.settings_appearance_contrast_note))

            state.blackBackground?.let { black ->
                ListGroup(Modifier.padding(top = ItmoTheme.spacing.compact)) {
                    item {
                        SwitchListRow(
                            text(SettingsStrings.settings_dark_black_title),
                            checked = black,
                            onCheckedChange = actions.onBlackBackground,
                            modifier = Modifier.testTag(AppearanceTags.BLACK),
                            subtitle = text(SettingsStrings.settings_dark_black_description),
                        )
                    }
                }
            }
            state.footer?.let { ListGroupNote(it.asString(), Modifier.padding(top = ItmoTheme.spacing.compact)) }
        }
    }
}

/**
 * Every colour as a circle of the primary it gives in the picked palette, named under it, three to a row; a tap applies
 * it. Under `Монохром` they stay in their hues, dimmed and inert, since the palette ignores them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(state: AppearanceUiState, actions: AppearanceActions) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(ItmoTheme.spacing.cardPadding)) {
        val gap = ItmoTheme.spacing.related
        val cell = (maxWidth - gap * (SWATCH_COLUMNS - 1)) / SWATCH_COLUMNS
        FlowRow(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .alpha(if (state.accentApplies) 1f else DISABLED_ALPHA),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
            maxItemsInEachRow = SWATCH_COLUMNS,
        ) {
            state.accents.forEach { option ->
                val label = option.label.asString()
                val swatch = accentSwatch(state.theme.copy(accent = option.accent), label)
                val selected = option.accent == state.accent
                Column(
                    Modifier
                        .width(cell)
                        .clip(ItmoTheme.shapes.medium)
                        .selectable(selected, enabled = state.accentApplies, role = Role.RadioButton) {
                            actions.onAccent(option.accent)
                        }
                        .testTag(AppearanceTags.accent(option.accent))
                        .padding(vertical = ItmoTheme.spacing.related),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.related),
                ) {
                    Circle(swatch.color, swatch.contentColor, selected)
                    Text(
                        label,
                        color = if (selected) ItmoTheme.colorScheme.onSurface else ItmoTheme.colorScheme.onSurfaceVariant,
                        style = ItmoTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** A colour circle; the picked one is ringed in `onSurface` and checked in its content colour. */
@Composable
private fun Circle(color: Color, content: Color, selected: Boolean) {
    Box(
        Modifier
            .size(SwatchSize)
            .then(if (selected) Modifier.border(RingWidth, ItmoTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .padding(if (selected) RingGap else 0.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, ItmoTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(Res.drawable.ic_check), null, Modifier.size(CheckSize), tint = content)
    }
}

/**
 * Every palette as three dots of what it makes of the picked colour (primary, its container, tertiary) in a card with
 * its name; the row scrolls sideways and opens with the picked one in view, outlined, its left neighbour peeking.
 */
@Composable
private fun Palettes(state: AppearanceUiState, actions: AppearanceActions) {
    val picked = state.styles.indexOfFirst { it.style == state.theme.style }
    LazyRow(
        Modifier.fillMaxWidth().selectableGroup(),
        state = rememberLazyListState(initialFirstVisibleItemIndex = (picked - 1).coerceAtLeast(0)),
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        items(state.styles, key = { it.style.name }) { option -> Palette(state, option, actions) }
    }
}

@Composable
private fun Palette(state: AppearanceUiState, option: StyleOption, actions: AppearanceActions) {
    val selected = option.style == state.theme.style
    val scheme = colorSchemeOf(ColorSource.Theme(state.theme.copy(style = option.style)), dark = isSystemInDarkTheme())
    val shape = ItmoTheme.shapes.cardContent
    Column(
        Modifier
            .widthIn(min = PaletteWidth)
            .clip(shape)
            .background(ItmoTheme.colorScheme.surfaceContainerLow)
            .then(
                if (selected) Modifier.border(RingWidth, ItmoTheme.colorScheme.primary, shape) else Modifier,
            )
            .selectable(selected, role = Role.RadioButton) { actions.onStyle(option.style) }
            .testTag(AppearanceTags.style(option.style))
            .padding(ItmoTheme.spacing.content),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        Box(Modifier.size(width = DotsWidth, height = DotSize)) {
            listOf(scheme.primary, scheme.primaryContainer, scheme.tertiary).forEachIndexed { index, color ->
                Box(
                    Modifier
                        .offset(x = DotStep * index)
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, ItmoTheme.colorScheme.surfaceContainerLow, CircleShape),
                )
            }
        }
        Text(
            option.label.asString(),
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun colorPickerLabels() = ColorPickerLabels(
    hue = text(SettingsStrings.settings_color_picker_hue),
    saturation = text(SettingsStrings.settings_color_picker_saturation),
    brightness = text(SettingsStrings.settings_color_picker_brightness),
    hex = text(SettingsStrings.settings_color_picker_hex),
    hexError = text(SettingsStrings.settings_color_picker_hex_error),
    preview = text(SettingsStrings.settings_color_picker_preview),
)

@Composable
private fun text(key: String): String = UiText.Res(key).asString()

private const val SWATCH_COLUMNS = 3
private val SwatchSize = 48.dp
private val RingWidth = 2.dp
private val RingGap = 4.dp
private val CheckSize = 20.dp
private val PaletteWidth = 104.dp
private val DotSize = 28.dp
private val DotStep = 18.dp
private val DotsWidth = DotSize + DotStep * 2
private const val DISABLED_ALPHA = 0.38f
