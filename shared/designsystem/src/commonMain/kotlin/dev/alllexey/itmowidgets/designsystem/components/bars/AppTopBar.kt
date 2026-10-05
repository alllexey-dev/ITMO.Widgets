package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * The small top bar of a contextual screen: an optional navigation button (back or close), a concise title of up to
 * two lines and trailing actions, on `surface`. It draws no window insets; the host pads the system bars, or the
 * caller adds `windowInsetsPadding` to [modifier].
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(modifier.fillMaxWidth(), color = ItmoTheme.colorScheme.surface) {
        Row(
            Modifier
                .heightIn(min = BarHeight)
                .padding(horizontal = BarEdgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Text(
                title,
                Modifier
                    .weight(1f)
                    .padding(start = if (navigation == null) TitleEdgeInset else TitleNavigationGap, end = TitleEdgeInset)
                    .semantics { heading() },
                color = ItmoTheme.colorScheme.onSurface,
                style = ItmoTheme.typography.titleLarge,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
    }
}

/** A navigation button or an action of [AppTopBar]: a 24 dp icon in a 48 dp target, labelled for TalkBack. */
@Composable
fun AppTopBarAction(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = label, tint = ItmoTheme.colorScheme.onSurface)
    }
}

/** `?attr/actionBarSize`, the MaterialToolbar height of today's contextual screens. */
private val BarHeight = 56.dp

/** The 4 dp row padding of the hand-built headers (`fragment_friends.xml`). */
private val BarEdgePadding = 4.dp

/** The headers' `layout_marginStart` between the back button and the title. */
private val TitleNavigationGap = 8.dp

/** With [BarEdgePadding], the 16 dp screen margin before a title without navigation and after the last word. */
private val TitleEdgeInset = 12.dp

/** `fragment_schedule_changes.xml` lets a title take two lines. */
private const val TITLE_MAX_LINES = 2
