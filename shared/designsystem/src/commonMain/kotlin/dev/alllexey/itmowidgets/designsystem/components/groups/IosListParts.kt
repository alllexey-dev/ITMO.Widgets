package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.theme.LocalItmoIosColors
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

// The pieces of an inset-grouped list that the iOS variants of the group, settings and row parts share: the row's
// separator, the pressed cell and the accessories. Material never reaches them.

/**
 * Where the separator under a row starts, from the row's leading edge: the start of its text. A kit row provides it
 * around its layout ([IosListRow]); the separator that `connectedGroupItem` draws reads it on the row's node.
 */
internal val LocalIosSeparatorInset = staticCompositionLocalOf { IosMetrics.separatorInset }

/**
 * The frame of a kit row under the iOS style: its clickable, selectable or toggleable shows the pressed cell
 * ([IosCellHighlight]) instead of a ripple, and the separator under it starts at [separatorInset]. Both reach the
 * modifiers of the row's own layout node, the caller's `connectedGroupItem` included, since they read the locals
 * where that node stands. Under Material it only draws [content].
 */
@Composable
internal fun IosListRow(separatorInset: Dp, content: @Composable () -> Unit) {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Material) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalIndication provides IosCellHighlight,
        LocalIosSeparatorInset provides separatorInset,
        content = content,
    )
}

/**
 * The hairline under a row of an inset group: [IosMetrics.separatorThickness] in [color], from the row's text start
 * ([LocalIosSeparatorInset]) to [IosMetrics.separatorTrailingInset] before its trailing edge, drawn over the row as
 * `UITableViewCell` draws its separator.
 */
internal fun Modifier.iosSeparator(color: Color): Modifier = this then IosSeparatorElement(color)

private data class IosSeparatorElement(val color: Color) : ModifierNodeElement<IosSeparatorNode>() {
    override fun create() = IosSeparatorNode(color)

    override fun update(node: IosSeparatorNode) {
        node.color = color
        node.invalidateDraw()
    }
}

private class IosSeparatorNode(var color: Color) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
        val start = currentValueOf(LocalIosSeparatorInset).toPx()
        val end = IosMetrics.separatorTrailingInset.toPx()
        val thickness = IosMetrics.separatorThickness.toPx()
        val left = if (layoutDirection == LayoutDirection.Ltr) start else end
        drawRect(
            color,
            topLeft = Offset(left, size.height - thickness),
            size = Size((size.width - start - end).coerceAtLeast(0f), thickness),
        )
    }
}

/**
 * A pressed cell of an inset-grouped list: [ItmoIosColors.cellHighlight] behind the row's content while a press
 * lasts, no ripple. UIKit lifts the highlight with a fade; a static fill is enough for a tap.
 */
internal object IosCellHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = IosCellHighlightNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = HASH
}

private const val HASH = 0x105C311

private class IosCellHighlightNode(private val source: InteractionSource) :
    Modifier.Node(),
    DrawModifierNode,
    CompositionLocalConsumerModifierNode {
    private val presses = mutableListOf<PressInteraction.Press>()

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (presses.isNotEmpty()) drawRect(currentValueOf(LocalItmoIosColors).cellHighlight)
        drawContent()
    }
}

/**
 * The disclosure indicator of a row that leads further, in `tertiaryLabel`, ending
 * [IosMetrics.disclosureTrailingInset] before the cell's edge when the row pads by [IosMetrics.rowHorizontalPadding].
 * The Material Symbols chevron is scaled so its glyph fills UIKit's 10.33 x 14 accessory, centred on it.
 */
@Composable
internal fun IosDisclosure(modifier: Modifier = Modifier) {
    IosAccessory(
        painterResource(Res.drawable.ic_chevron_right),
        IosMetrics.disclosureWidth,
        IosMetrics.disclosureHeight,
        DisclosureIconSize,
        IosMetrics.disclosureTrailingInset,
        ItmoTheme.iosColors.tertiaryLabel,
        modifier,
    )
}

/**
 * The checkmark of a picked row in the tint (`colorScheme.primary`); an unpicked row keeps its place but shows
 * nothing, so the text does not move when the pick changes.
 */
@Composable
internal fun IosCheckmark(visible: Boolean, modifier: Modifier = Modifier) {
    IosAccessory(
        painterResource(Res.drawable.ic_check),
        IosMetrics.checkmarkWidth,
        IosMetrics.checkmarkHeight,
        CheckmarkIconSize,
        IosMetrics.checkmarkTrailingInset,
        ItmoTheme.colorScheme.primary,
        modifier.alpha(if (visible) 1f else 0f),
    )
}

@Composable
private fun IosAccessory(
    icon: Painter,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    trailingInset: Dp,
    tint: Color,
    modifier: Modifier,
) {
    Box(
        modifier
            .padding(start = IosMetrics.accessoryGap, end = trailingInset - IosMetrics.rowHorizontalPadding)
            .size(width, height),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.requiredSize(iconSize), tint = tint)
    }
}

/**
 * `ic_chevron_right`'s glyph is 10.6 of 24 dp high (`M348..616 x 268..692` of 960, centred); at 32 dp it is 14 high,
 * UIKit's indicator.
 */
private val DisclosureIconSize = 32.dp

/** `ic_check`'s glyph is 12 of 24 dp high (`239..720` of 960, centred); at 26 dp it is 13 high, as UIKit's mark. */
private val CheckmarkIconSize = 26.dp
