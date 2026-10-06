package dev.alllexey.itmowidgets.designsystem.components.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.IOS_PRESSED_ALPHA
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_check
import org.jetbrains.compose.resources.painterResource

/** How a button of an [IosAlertSurface] reads: `UIAlertAction.Style` and the alert's `preferredAction`. */
internal enum class IosAlertButtonKind {
    /** `.default` and `.cancel`: the label colour on the fill. */
    Plain,

    /** The preferred action: filled in the tint, its label semibold. */
    Preferred,

    /** `.destructive`: system red on the fill. */
    Destructive,
}

/** One button of an [IosAlertSurface]; while [inProgress] a spinner covers the label and taps do nothing. */
@Immutable
internal data class IosAlertButton(
    val label: String,
    val onClick: () -> Unit,
    val kind: IosAlertButtonKind = IosAlertButtonKind.Plain,
    val enabled: Boolean = true,
    val inProgress: Boolean = false,
)

/**
 * An iOS alert drawn in Compose over the kit's dialog window, at the pinned runtime's measures (iOS 27, DS-IOS-05):
 * [IosMetrics.alertWidth] wide with [IosMetrics.alertRadius] corners on the opaque overlay colour, the title in
 * semibold headline and the [message] in subheadline, both on the leading edge (a lone title is regular and centred,
 * as UIKit draws it; without a title the message starts at the top), an optional [content] (the caller makes it
 * scroll) and [buttons] as 48 pt capsules ([IosAlertButtons]).
 */
@Composable
internal fun IosAlertSurface(
    title: String?,
    buttons: List<IosAlertButton>,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = ItmoTheme.iosColors
    val lone = message == null && content == null
    Column(
        modifier
            .width(IosMetrics.alertWidth)
            .background(colors.overlay, RoundedCornerShape(IosMetrics.alertRadius))
            .then(if (title != null) Modifier.semantics { paneTitle = title } else Modifier)
            .padding(top = IosMetrics.alertTextTop),
    ) {
        if (title != null) {
            Text(
                title,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IosMetrics.alertTextInset)
                    .semantics { heading() },
                color = colors.label,
                style = if (lone) ItmoTheme.typography.bodyLarge else ItmoTheme.typography.titleMedium,
                textAlign = if (lone) TextAlign.Center else TextAlign.Start,
            )
        }
        if (message != null) {
            Text(
                message,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IosMetrics.alertTextInset)
                    .padding(top = if (title != null) IosMetrics.alertMessageGap else 0.dp),
                color = colors.secondaryLabel,
                style = ItmoTheme.typography.bodyMedium,
            )
        }
        if (content != null) {
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .padding(top = IosMetrics.alertContentGap),
            ) {
                content()
            }
        }
        if (buttons.isNotEmpty()) {
            // A long stack of actions scrolls, as UIKit's action group does; beside a content, which takes the
            // room left, the buttons stay whole.
            IosAlertButtons(
                buttons,
                Modifier
                    .then(
                        if (content == null) {
                            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        start = IosMetrics.alertButtonInset,
                        end = IosMetrics.alertButtonInset,
                        top = if (content == null) IosMetrics.alertButtonsGap else IosMetrics.alertContentGap,
                    ),
            )
        }
        Spacer(Modifier.height(IosMetrics.alertButtonInset))
    }
}

/**
 * Two buttons share a row when both labels fit half of it, as UIKit lays out a pair; otherwise, and for any other
 * number, they stack.
 */
@Composable
internal fun IosAlertButtons(buttons: List<IosAlertButton>, modifier: Modifier = Modifier) {
    Layout(
        content = { buttons.forEach { IosAlertButtonView(it) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = IosMetrics.alertButtonSpacing.roundToPx()
        val half = (width - gap) / 2
        val paired = measurables.size == 2 && measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= half }
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(if (paired) half else width)) }
        val height = if (paired) {
            placeables.maxOf { it.height }
        } else {
            placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        }
        layout(width, height) {
            var offset = 0
            placeables.forEach { placeable ->
                if (paired) {
                    placeable.placeRelative(offset, 0)
                    offset += half + gap
                } else {
                    placeable.placeRelative(0, offset)
                    offset += placeable.height + gap
                }
            }
        }
    }
}

/**
 * A capsule of `_UIAlertControllerActionView` (radius 24, half its one-line height, so a wrapped label keeps the
 * corners), dimmed while pressed instead of a ripple.
 */
@Composable
private fun IosAlertButtonView(button: IosAlertButton) {
    val colors = ItmoTheme.iosColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (container, label) = when {
        !button.enabled -> colors.tertiarySystemFill to colors.tertiaryLabel
        button.kind == IosAlertButtonKind.Preferred -> ItmoTheme.colorScheme.primary to ItmoTheme.colorScheme.onPrimary
        button.kind == IosAlertButtonKind.Destructive -> colors.tertiarySystemFill to colors.systemRed
        else -> colors.tertiarySystemFill to colors.label
    }
    val weight = if (button.kind == IosAlertButtonKind.Preferred) FontWeight.SemiBold else FontWeight.Medium
    Box(
        Modifier
            .heightIn(min = IosMetrics.alertButtonHeight)
            .clickable(interaction, indication = null, enabled = button.enabled, role = Role.Button) {
                if (!button.inProgress) button.onClick()
            }
            .graphicsLayer { alpha = if (pressed) IOS_PRESSED_ALPHA else 1f }
            .background(container, RoundedCornerShape(IosMetrics.alertButtonHeight / 2))
            .padding(horizontal = IosMetrics.alertButtonInset, vertical = IosButtonLabelPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            button.label,
            Modifier.alpha(if (button.inProgress) 0f else 1f),
            color = label,
            style = ItmoTheme.typography.bodyLarge.copy(fontWeight = weight),
            textAlign = TextAlign.Center,
        )
        if (button.inProgress) ItmoActivityIndicator(color = label)
    }
}

/**
 * A row of a list inside an alert (the choice and the report dialogs): [text] in body on the alert's text inset, a
 * trailing checkmark in the tint while [checked], a hairline above every row but the first. [interaction] carries the
 * row's click or selection semantics; [enabled] false greys the text out.
 */
@Composable
internal fun IosAlertCheckRow(
    text: String,
    checked: Boolean,
    first: Boolean,
    interaction: Modifier,
    enabled: Boolean = true,
) {
    val colors = ItmoTheme.iosColors
    Column(Modifier.fillMaxWidth()) {
        if (!first) {
            Box(
                Modifier
                    .padding(horizontal = IosMetrics.alertTextInset)
                    .fillMaxWidth()
                    .height(IosMetrics.separatorThickness)
                    .background(colors.separator),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ItmoTheme.spacing.touchTarget)
                .then(interaction)
                .padding(horizontal = IosMetrics.alertTextInset, vertical = IosRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                Modifier.weight(1f),
                color = if (enabled) colors.label else colors.tertiaryLabel,
                style = ItmoTheme.typography.bodyLarge,
            )
            Box(Modifier.padding(start = IosCheckGap).size(IosCheckSize), contentAlignment = Alignment.Center) {
                if (checked) {
                    Icon(
                        painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = if (enabled) ItmoTheme.colorScheme.primary else colors.tertiaryLabel,
                    )
                }
            }
        }
    }
}

/**
 * The label's top and bottom inset in a capsule: a 48 pt button around one 22 pt body line keeps 13 pt; a larger font
 * grows the capsule instead of clipping it.
 */
private val IosButtonLabelPadding = 13.dp

/** A list row's top and bottom inset: one body line in a 44 pt row. */
private val IosRowVerticalPadding = 11.dp

/**
 * The checkmark's box: the Material Symbols check at 24 dp draws a glyph close to the 19 x 17 pt `checkmark` accessory
 * of a cell (DS-IOS-05); glyph-exact checks are DS-IOS-06's.
 */
private val IosCheckSize = 24.dp

/** Between the row's text and its checkmark. */
private val IosCheckGap = 8.dp
