package dev.alllexey.itmowidgets.designsystem.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_close
import org.jetbrains.compose.resources.painterResource

/** The close button of a [SheetScaffold]: [label] is what TalkBack reads. */
@Immutable
data class SheetClose(val label: String, val onClick: () -> Unit)

/** Where a [SheetScaffold]'s close button sits. */
enum class SheetClosePlacement {
    /** After the title, as in the links, scores and editor sheets. */
    End,

    /** Before the title in a 56 dp bar, as in the details sheets' toolbar. */
    Start,
}

/**
 * The body of a bottom sheet: an optional drag handle, a header with [title], an optional [subtitle] and [close],
 * one content area and an optional [footer]. The content area is bounded: it gets the height the sheet has left after
 * the header and the footer, so a scrolling list inside it scrolls instead of pushing the footer away, and it is at
 * least [contentMinHeight] tall, so loading, content and error switch without the sheet jumping (the 288 dp cases).
 * The host owns the container (colour, corners, insets, dismissal): `ItmoBottomSheetFragment` on Android.
 *
 * Under the iOS style the host is SwiftUI's `.sheet`, which draws the grabber, so no [handle] is drawn; the header is
 * a sheet's navigation bar on the grouped background: the title in headline (subheadline semibold over a [subtitle] in
 * caption) centred and kept clear of the wider side, [close] as a text button in the tint at the leading edge for
 * [SheetClosePlacement.Start] and as the circled close mark at the trailing edge for [SheetClosePlacement.End].
 */
@Composable
fun SheetScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    handle: Boolean = true,
    close: SheetClose? = null,
    closePlacement: SheetClosePlacement = SheetClosePlacement.End,
    contentMinHeight: Dp = 0.dp,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ios = ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
    BoundedBodyLayout(
        modifier = if (ios) modifier.background(ItmoTheme.iosColors.groupedSheetBackground) else modifier,
        top = {
            if (ios) {
                IosSheetHeader(title, subtitle, close, closePlacement)
            } else {
                Column(Modifier.fillMaxWidth()) {
                    if (handle) SheetHandle(Modifier.align(Alignment.CenterHorizontally))
                    when (closePlacement) {
                        SheetClosePlacement.End -> EndCloseHeader(title, subtitle, close, handle)
                        SheetClosePlacement.Start -> StartCloseHeader(title, subtitle, close)
                    }
                }
            }
        },
        body = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = ItmoTheme.spacing.compact)
                    .heightIn(min = contentMinHeight),
                content = content,
            )
        },
        bottom = {
            Column(Modifier.fillMaxWidth().padding(bottom = ItmoTheme.spacing.group)) {
                footer?.invoke(this)
            }
        },
    )
}

/** `Widget.ItmoWidgets.SheetHandle`: a 32 by 4 dp pill in `outlineVariant`, 8 dp below the sheet's edge. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = ItmoTheme.spacing.compact)
            .size(HandleWidth, HandleHeight)
            .background(ItmoTheme.colorScheme.outlineVariant, ItmoTheme.shapes.full),
    )
}

/** `sheet_subject_links.xml` and `sheet_review_editor.xml`: title and subtitle on the margin, close at the end. */
@Composable
private fun EndCloseHeader(title: String, subtitle: String?, close: SheetClose?, handle: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = ItmoTheme.spacing.screenMargin,
                end = if (close == null) ItmoTheme.spacing.screenMargin else ItmoTheme.spacing.related,
                top = if (handle) ItmoTheme.spacing.group else ItmoTheme.spacing.compact,
            ),
        // Under a handle the close button lines up with the title's top; without one it centres on the text.
        verticalAlignment = if (handle) Alignment.Top else Alignment.CenterVertically,
    ) {
        HeaderTexts(
            title,
            subtitle,
            Modifier
                .weight(1f)
                .padding(vertical = if (handle) 0.dp else ItmoTheme.spacing.compact),
        )
        if (close != null) CloseButton(close, ItmoTheme.colorScheme.onSurfaceVariant)
    }
}

/** `fragment_lesson_details.xml`'s toolbar: close at the start of a 56 dp bar, the title after it. */
@Composable
private fun StartCloseHeader(title: String, subtitle: String?, close: SheetClose?) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = BarHeight)
            .padding(horizontal = BarEdgePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (close != null) CloseButton(close, ItmoTheme.colorScheme.onSurface)
        HeaderTexts(
            title,
            subtitle,
            Modifier
                .weight(1f)
                .padding(start = if (close == null) TitleEdgeInset else TitleNavigationGap, end = TitleEdgeInset),
        )
    }
}

@Composable
private fun HeaderTexts(title: String, subtitle: String?, modifier: Modifier) {
    Column(modifier) {
        Text(
            title,
            Modifier.semantics { heading() },
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleLarge,
        )
        if (subtitle != null) {
            Text(subtitle, color = ItmoTheme.colorScheme.onSurfaceVariant, style = ItmoTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CloseButton(close: SheetClose, tint: Color) {
    IconButton(onClick = close.onClick) {
        Icon(painterResource(Res.drawable.ic_close), contentDescription = close.label, tint = tint)
    }
}

/**
 * Header, body and footer stacked: the header and the footer take their height first, the body gets at most what is
 * left of a bounded height (unbounded stays unbounded), and the whole is as tall as its parts.
 */
@Composable
private fun BoundedBodyLayout(
    modifier: Modifier,
    top: @Composable () -> Unit,
    body: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    Layout(contents = listOf(top, body, bottom), modifier = modifier) { (topMeasurables, bodyMeasurables, bottomMeasurables), constraints ->
        val loose = constraints.copy(minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 0, minHeight = 0)
        val topPlaceables = topMeasurables.map { it.measure(loose) }
        val bottomPlaceables = bottomMeasurables.map { it.measure(loose) }
        val fixedHeight = (topPlaceables + bottomPlaceables).sumOf { it.height }
        val bodyMaxHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - fixedHeight).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val bodyPlaceables = bodyMeasurables.map { it.measure(loose.copy(maxHeight = bodyMaxHeight)) }
        val placeables = topPlaceables + bodyPlaceables + bottomPlaceables
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
        val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

/** `Widget.ItmoWidgets.SheetHandle`'s 32 by 4 dp. */
private val HandleWidth = 32.dp
private val HandleHeight = 4.dp

/** `?attr/actionBarSize`, the MaterialToolbar of the details sheets. */
private val BarHeight = 56.dp

/** The toolbar's 4 dp content inset before the navigation button. */
private val BarEdgePadding = 4.dp

/** Between the close button and the title, as `AppTopBar`. */
private val TitleNavigationGap = 8.dp

/** With [BarEdgePadding], the 16 dp screen margin around a title without a close button. */
private val TitleEdgeInset = 12.dp
