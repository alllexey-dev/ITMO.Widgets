package dev.alllexey.itmowidgets.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.designsystem.kit.IconTile
import dev.alllexey.itmowidgets.designsystem.kit.Tone
import dev.alllexey.itmowidgets.designsystem.theme.ColorSource
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.compose.strings.Res
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_dark
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_light
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_sample
import dev.alllexey.itmowidgets.shared.compose.strings.settings_appearance_sample_action
import dev.alllexey.itmowidgets.shared.designsystem.ic_star
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * [theme] on a small screen of the app, once in the light and once in the dark theme, whichever the phone shows now:
 * a heading, a card with a tile and a switch, a filled button. So contrast and the black background show even in the
 * light theme. It is a picture: TalkBack reads it as one element and nothing in it reacts.
 */
@Composable
internal fun AppearanceSample(theme: ThemeSpec, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.settings_appearance_sample)
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(ItmoTheme.spacing.compact),
    ) {
        SampleScreen(theme, dark = false, stringResource(Res.string.settings_appearance_light), Modifier.weight(1f))
        SampleScreen(theme, dark = true, stringResource(Res.string.settings_appearance_dark), Modifier.weight(1f))
    }
}

@Composable
private fun SampleScreen(theme: ThemeSpec, dark: Boolean, label: String, modifier: Modifier) {
    val caption = ItmoTheme.colorScheme.onSurfaceVariant
    val frame = ItmoTheme.colorScheme.outlineVariant
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ItmoTheme(dark = dark, colorSource = ColorSource.Theme(theme)) {
            val colors = ItmoTheme.colorScheme
            val shape = ItmoTheme.shapes.cardContent
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.background)
                    .border(1.dp, frame, shape)
                    .padding(SamplePadding),
                verticalArrangement = Arrangement.spacedBy(SampleGap),
            ) {
                Line(colors.onSurface, 0.55f, height = 10.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(ItmoTheme.shapes.medium)
                        .background(colors.surfaceContainerLow)
                        .padding(SampleGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SampleGap),
                ) {
                    IconTile(painterResource(KitRes.drawable.ic_star), Tone.Primary, size = TileSize)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LineGap)) {
                        Line(colors.onSurface, 0.9f)
                        Line(colors.onSurfaceVariant, 0.6f)
                    }
                    SampleSwitch(colors.primary, colors.onPrimary)
                }
                Button(onClick = {}, Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.settings_appearance_sample_action), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(
            label,
            Modifier.padding(top = ItmoTheme.spacing.related),
            color = caption,
            style = ItmoTheme.typography.labelMedium,
        )
    }
}

/** A line of text as a rounded bar of its colour. */
@Composable
private fun Line(color: Color, fraction: Float, height: Dp = 6.dp) {
    Box(Modifier.fillMaxWidth(fraction).height(height).clip(CircleShape).background(color))
}

/** A switch that is on, in the size the sample has room for. */
@Composable
private fun SampleSwitch(track: Color, thumb: Color) {
    Box(
        Modifier.width(SwitchWidth).height(SwitchHeight).clip(CircleShape).background(track).padding(ThumbInset),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(Modifier.size(SwitchHeight - ThumbInset * 2).clip(CircleShape).background(thumb))
    }
}

private val SamplePadding = 12.dp
private val SampleGap = 8.dp
private val TileSize = 28.dp
private val SwitchWidth = 30.dp
private val SwitchHeight = 18.dp
private val ThumbInset = 3.dp
private val LineGap = 4.dp
