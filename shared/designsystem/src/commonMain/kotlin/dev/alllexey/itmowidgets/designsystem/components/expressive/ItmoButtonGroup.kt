package dev.alllexey.itmowidgets.designsystem.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoHapticEvent
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.platform.rememberItmoHaptics
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion

/**
 * A single choice of two to four short [options] with a selection always made, side by side at equal widths: the
 * friend picker's scope, the sport tabs, a single-select chip set. With [ItmoTheme.expressive] it is the connected
 * button group of toggle buttons; otherwise today's segmented buttons. Under the iOS style it is `UISegmentedControl`:
 * one track with a thumb that slides to the chosen segment and a selection haptic. [onSelect] gets the index of a
 * newly chosen option; tapping the selected one does nothing.
 */
@Composable
fun ItmoButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size in 2..MAX_OPTIONS) { "A button group holds 2..$MAX_OPTIONS options, got ${options.size}" }
    require(selectedIndex in options.indices) { "selectedIndex $selectedIndex is outside ${options.indices}" }
    val choose = { index: Int -> if (index != selectedIndex) onSelect(index) }
    when {
        ItmoTheme.platformStyle == ItmoPlatformStyle.Ios -> IosSegmentedControl(options, selectedIndex, choose, modifier)
        ItmoTheme.expressive -> ConnectedGroup(options, selectedIndex, choose, modifier)
        else -> SegmentedGroup(options, selectedIndex, choose, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedGroup(options: List<String>, selectedIndex: Int, choose: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            ToggleButton(
                checked = index == selectedIndex,
                onCheckedChange = { choose(index) },
                modifier = Modifier.weight(1f),
                shapes = connectedShapes(index, options.lastIndex),
            ) { OptionLabel(label) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun connectedShapes(index: Int, lastIndex: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

@Composable
private fun SegmentedGroup(options: List<String>, selectedIndex: Int, choose: (Int) -> Unit, modifier: Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { choose(index) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.weight(1f),
                label = { OptionLabel(label) },
            )
        }
    }
}

/**
 * `UISegmentedControl`: the track (`tertiarySystemFill`) and the selected segment's capsule thumb are drawn behind
 * equal segments that take the full 44 pt target height; the track is [IosMetrics.segmentedHeight] high and grows with
 * the text. Labels are 13 pt like UIKit's (`UISegmentLabel`): medium on the selected segment, regular on the others.
 */
@Composable
private fun IosSegmentedControl(options: List<String>, selectedIndex: Int, choose: (Int) -> Unit, modifier: Modifier) {
    val haptics = rememberItmoHaptics()
    val motion = ItmoTheme.motion
    val colors = ItmoTheme.iosColors
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (rememberReducedMotion()) snap() else tween(motion.standardMillis, easing = motion.easing),
        label = "thumb",
    )
    val label = ItmoTheme.typography.bodySmall
    val target = ItmoTheme.spacing.touchTarget
    // The track sits centred in the target; a label taller than the track grows both.
    val outside = (target - IosMetrics.segmentedHeight) / 2
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = target)
            .height(IntrinsicSize.Min)
            .selectableGroup()
            .drawBehind {
                val track = Size(size.width, maxOf(IosMetrics.segmentedHeight.toPx(), size.height - outside.toPx() * 2))
                val top = (size.height - track.height) / 2
                drawRoundRect(colors.tertiarySystemFill, Offset(0f, top), track, CornerRadius(track.height / 2))
                val inset = IosMetrics.segmentedThumbInset.toPx()
                val segment = size.width / options.size
                val thumb = Size(segment - inset * 2, track.height - inset * 2)
                drawRoundRect(
                    colors.segmentedThumb,
                    Offset(segment * position + inset, top + inset),
                    thumb,
                    CornerRadius(thumb.height / 2),
                )
            },
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected, interactionSource = null, indication = null, role = Role.RadioButton) {
                        if (!selected) haptics.perform(ItmoHapticEvent.Selection)
                        choose(index)
                    }
                    .padding(horizontal = SegmentLabelPadding, vertical = outside + IosMetrics.segmentedThumbInset),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    color = ItmoTheme.colorScheme.onSurface,
                    style = label.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OptionLabel(label: String) {
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Keeps a segment's label off the rounded ends of the thumb. */
private val SegmentLabelPadding = 8.dp

/** More options than this need a menu or chips, not a group of equal buttons. */
private const val MAX_OPTIONS = 4
