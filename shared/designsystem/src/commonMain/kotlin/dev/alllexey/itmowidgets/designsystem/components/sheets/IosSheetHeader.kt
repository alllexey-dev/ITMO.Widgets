package dev.alllexey.itmowidgets.designsystem.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.IosBarButton
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import org.jetbrains.compose.resources.painterResource

/**
 * The navigation bar of a sheet presented by SwiftUI (DS-IOS-05, measured on iOS 27): [IosMetrics.navigationBarHeight]
 * high, [IosMetrics.sheetBarTop] below the sheet's edge where the host's grabber sits, bar buttons
 * [IosMetrics.barEdgeInset] from the sides. The title is headline alone, or subheadline semibold over the subtitle in
 * caption, centred on the bar and kept clear of the wider side; both wrap rather than lose words.
 */
@Composable
internal fun IosSheetHeader(
    title: String,
    subtitle: String?,
    close: SheetClose?,
    closePlacement: SheetClosePlacement,
) {
    val leading = close?.takeIf { closePlacement == SheetClosePlacement.Start }
    val trailing = close?.takeIf { closePlacement == SheetClosePlacement.End }
    Layout(
        contents = listOf(
            { if (leading != null) IosTextClose(leading) },
            { if (trailing != null) IosCircledClose(trailing) },
            { IosSheetTitle(title, subtitle) },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = IosMetrics.sheetBarTop)
            .heightIn(min = IosMetrics.navigationBarHeight)
            .padding(horizontal = IosMetrics.barEdgeInset),
    ) { (start, end, heading), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val lead = start.firstOrNull()?.measure(loose)
        val trail = end.firstOrNull()?.measure(loose)
        val side = maxOf(lead?.width ?: 0, trail?.width ?: 0)
        val gap = TitleGap.roundToPx()
        val room = (constraints.maxWidth - 2 * (side + gap)).coerceAtLeast(0)
        val text = heading.first().measure(Constraints(maxWidth = room))
        val height = maxOf(constraints.minHeight, lead?.height ?: 0, trail?.height ?: 0, text.height)
        layout(constraints.maxWidth, height) {
            lead?.placeRelative(0, (height - lead.height) / 2)
            trail?.placeRelative(constraints.maxWidth - trail.width, (height - trail.height) / 2)
            text.placeRelative((constraints.maxWidth - text.width) / 2, (height - text.height) / 2)
        }
    }
}

@Composable
private fun IosSheetTitle(title: String, subtitle: String?) {
    val colors = ItmoTheme.iosColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            Modifier.semantics { heading() },
            color = colors.label,
            style = if (subtitle == null) ItmoTheme.typography.titleMedium else ItmoTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                color = colors.secondaryLabel,
                // Caption 1 regular: `navigationItem.subtitle`'s 12 pt label; the type scale's caption role is medium.
                style = ItmoTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** `UIBarButtonItem(title:)`: [SheetClose.label] in medium body and the tint. */
@Composable
private fun IosTextClose(close: SheetClose) {
    IosBarButton(close.onClick) {
        Text(
            close.label,
            Modifier.padding(horizontal = TextClosePadding),
            color = ItmoTheme.colorScheme.primary,
            style = ItmoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/**
 * `UIBarButtonItem(systemItem: .close)`: the close mark in the label colour on a round platter, opaque on the fill
 * instead of glass, read as [SheetClose.label].
 */
@Composable
private fun IosCircledClose(close: SheetClose) {
    IosBarButton(close.onClick) {
        Box(
            Modifier
                .size(IosMetrics.sheetBarButtonPlatter)
                .background(ItmoTheme.iosColors.tertiarySystemFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(Res.drawable.ic_close),
                contentDescription = close.label,
                modifier = Modifier.size(CloseMarkSize),
                tint = ItmoTheme.iosColors.label,
            )
        }
    }
}

/** The least room between the title and the wider side's button. */
private val TitleGap = 8.dp

/** A text bar button's inset inside its target, as `AppTopBarTextAction`'s. */
private val TextClosePadding = 6.dp

/**
 * The Material Symbols close at the size whose glyph (7/12 of the box) matches the bar's 17 x 16 pt `xmark` image;
 * glyph-exact checks are DS-IOS-06's.
 */
private val CloseMarkSize = 28.dp
