package dev.alllexey.itmowidgets.designsystem.components.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The two sizes of the empty, error and loading family. */
enum class ContentStateSize {
    /** Fills the screen's content area; 64 dp icon. */
    Full,

    /** Inside a section, card or sheet, as tall as its content; 56 dp icon. */
    Compact,
}

/** How a [ContentState]'s action looks: tonal for retry and ordinary actions, filled for a primary way out. */
enum class ContentStateActionStyle { Tonal, Filled }

/** The one action of a [ContentState]. */
@Immutable
data class ContentStateAction(
    val label: String,
    val onClick: () -> Unit,
    val style: ContentStateActionStyle = ContentStateActionStyle.Tonal,
)

/**
 * An empty or error state: an optional decorative icon, a title, an optional description and an optional action,
 * centred in the area the content would take (`Widget.ItmoWidgets.ContentState`). [ContentStateLoading] takes the same
 * area, so loading, content, empty and error switch without a layout jump.
 */
@Composable
fun ContentState(
    title: String,
    modifier: Modifier = Modifier,
    size: ContentStateSize = ContentStateSize.Full,
    icon: Painter? = null,
    description: String? = null,
    action: ContentStateAction? = null,
) {
    Column(
        modifier.stateArea(size).padding(ItmoTheme.spacing.statePadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, Modifier.size(size.iconSize()), tint = ItmoTheme.colorScheme.primary)
            Spacer(Modifier.height(ItmoTheme.spacing.group))
        }
        Text(
            title,
            color = ItmoTheme.colorScheme.onSurface,
            style = ItmoTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(ItmoTheme.spacing.compact))
            Text(
                description,
                color = ItmoTheme.colorScheme.onSurfaceVariant,
                style = ItmoTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(ActionGap))
            ContentStateButton(action)
        }
    }
}

/** The loading member of the family: an indeterminate indicator in `primary`, centred in the same area. */
@Composable
fun ContentStateLoading(
    modifier: Modifier = Modifier,
    size: ContentStateSize = ContentStateSize.Full,
) {
    Box(modifier.stateArea(size).padding(ItmoTheme.spacing.statePadding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ItmoTheme.colorScheme.primary)
    }
}

@Composable
private fun ContentStateButton(action: ContentStateAction) {
    when (action.style) {
        ContentStateActionStyle.Tonal -> FilledTonalButton(onClick = action.onClick) { Text(action.label) }
        ContentStateActionStyle.Filled -> Button(onClick = action.onClick) { Text(action.label) }
    }
}

private fun Modifier.stateArea(size: ContentStateSize): Modifier = when (size) {
    ContentStateSize.Full -> fillMaxSize()
    ContentStateSize.Compact -> fillMaxWidth()
}

@Composable
private fun ContentStateSize.iconSize(): Dp = when (this) {
    ContentStateSize.Full -> ItmoTheme.spacing.stateIcon
    ContentStateSize.Compact -> ItmoTheme.spacing.stateInlineIcon
}

/** `Widget.ItmoWidgets.ContentState.Action`'s 20 dp top margin, off the spacing scale. */
private val ActionGap = 20.dp
