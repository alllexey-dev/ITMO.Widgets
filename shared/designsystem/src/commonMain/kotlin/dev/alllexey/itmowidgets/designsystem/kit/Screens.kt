package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import org.jetbrains.compose.resources.painterResource

/** Test tags of the kit's screen frames. */
object ItmoScreenTags {
    const val ROOT_HEADER = "kit:root_header"
}

/**
 * The root screen of a tab: the root header ([title] and its one-line [subtitle] on the left, [actions] in the same
 * row, centred on the title block) that collapses from [RootHeaderTokens.Expanded] to a [RootHeaderTokens.Collapsed]
 * bar while the [content] scrolls, and an optional [floatingActionButton]. The navigation bar belongs to the shell;
 * window insets too: the frame pads none. [content] gets the padding to apply.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItmoRootScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { RootHeader(title, subtitle, actions, scroll) },
        floatingActionButton = floatingActionButton,
        containerColor = ItmoTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

/**
 * Material's large flexible bar puts the actions in a row above the title, so the header is the kit's own: one row,
 * the type shrinking from `displaySmall` and `titleMedium` to `titleLarge` and `labelMedium` as [scroll] collapses
 * it, the surface turning `surfaceContainer`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootHeader(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    scroll: TopAppBarScrollBehavior,
) {
    val density = LocalDensity.current
    val expanded = with(density) { (if (subtitle != null) RootHeaderTokens.Expanded else RootHeaderTokens.ExpandedTitleOnly).toPx() }
    val collapsed = with(density) { RootHeaderTokens.Collapsed.toPx() }
    SideEffect {
        if (scroll.state.heightOffsetLimit != collapsed - expanded) scroll.state.heightOffsetLimit = collapsed - expanded
    }
    val fraction = scroll.state.collapsedFraction
    val type = ItmoTheme.typography
    val colors = ItmoTheme.colorScheme
    Surface(color = lerp(colors.surface, colors.surfaceContainer, fraction), modifier = Modifier.testTag(ItmoScreenTags.ROOT_HEADER)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .height(with(density) { (expanded + scroll.state.heightOffset).coerceAtLeast(collapsed).toDp() })
                .padding(start = ItmoTheme.spacing.screenMargin, end = ItmoTheme.spacing.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(
                    title,
                    color = colors.onSurface,
                    style = lerp(type.displaySmall, type.titleLarge, fraction),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = colors.onSurfaceVariant,
                        style = lerp(type.titleMedium, type.labelMedium, fraction),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** An icon action of a header: [label] is what TalkBack reads; [badge] counts something new (the bell). */
@Composable
fun HeaderAction(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int = 0) {
    IconButton(onClick = onClick, modifier = modifier) {
        if (badge > 0) {
            BadgedBox(badge = { Badge { Text("$badge") } }) { Icon(icon, contentDescription = label) }
        } else {
            Icon(icon, contentDescription = label)
        }
    }
}

/**
 * A pushed screen (subject, person, a settings page): the back button, a medium flexible title over an optional
 * [subtitle] that collapses on scroll, [actions], no navigation bar. [backLabel] is what TalkBack reads for the back
 * button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItmoDetailScreen(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = { HeaderAction(painterResource(Res.drawable.ic_arrow_back), backLabel, onBack) },
                actions = actions,
                windowInsets = WindowInsets(0),
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = ItmoTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

/** The root header's heights. */
object RootHeaderTokens {
    /** Title and subtitle, expanded. */
    val Expanded = 112.dp

    /** A title without a subtitle, expanded. */
    val ExpandedTitleOnly = 96.dp

    /** Collapsed while the content scrolls. */
    val Collapsed = 64.dp
}
