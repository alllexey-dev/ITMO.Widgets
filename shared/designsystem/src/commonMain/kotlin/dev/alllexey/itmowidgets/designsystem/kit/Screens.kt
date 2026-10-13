package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_drop_down
import org.jetbrains.compose.resources.painterResource

/** Test tags of the kit's screen frames. */
object ItmoScreenTags {
    const val ROOT_HEADER = "kit:root_header"
}

/**
 * The root screen of a tab: the static root header ([title] and its one-line [subtitle] on the left, [actions] in the
 * same row, centred on the title block) that the [content] scrolls under, and an optional [floatingActionButton]. The
 * navigation bar belongs to the shell; window insets too: the frame pads none. [content] gets the padding to apply.
 * With [onTitleClick] the title is a menu (`Сентябрь ▾`, the schedule's month sheet) that TalkBack reads as
 * [titleClickLabel].
 */
@Composable
fun ItmoRootScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    titleClickLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = { RootHeader(title, subtitle, onTitleClick, titleClickLabel, actions) },
        floatingActionButton = floatingActionButton,
        containerColor = ItmoTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

/**
 * Material's large flexible bar puts the actions in a row above the title, so the header is the kit's own: one row,
 * `displaySmall` and `titleMedium`. It never collapses (owner, 2026-10-13).
 */
@Composable
private fun RootHeader(
    title: String,
    subtitle: String?,
    onTitleClick: (() -> Unit)?,
    titleClickLabel: String?,
    actions: @Composable RowScope.() -> Unit,
) {
    val type = ItmoTheme.typography
    val colors = ItmoTheme.colorScheme
    Surface(color = colors.surface, modifier = Modifier.testTag(ItmoScreenTags.ROOT_HEADER)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (subtitle != null) RootHeaderTokens.Height else RootHeaderTokens.HeightTitleOnly)
                .padding(start = ItmoTheme.spacing.screenMargin, end = ItmoTheme.spacing.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val menu = if (onTitleClick == null) {
                Modifier
            } else {
                Modifier.clip(ItmoTheme.shapes.medium).clickable(onClickLabel = titleClickLabel, role = Role.Button, onClick = onTitleClick)
            }
            Column(Modifier.weight(1f).then(menu).semantics(mergeDescendants = true) { heading() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        Modifier.weight(1f, fill = false),
                        color = colors.onSurface,
                        style = type.displaySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onTitleClick != null) {
                        Icon(painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null, tint = colors.onSurface)
                    }
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = colors.onSurfaceVariant,
                        style = type.titleMedium,
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
    /** Title and subtitle. */
    val Height = 112.dp

    /** A title without a subtitle. */
    val HeightTitleOnly = 96.dp
}
