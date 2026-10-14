package dev.alllexey.itmowidgets.designsystem.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
 * The root screen of a tab: the static root header ([title] and its one-line [subtitle] on one baseline, [actions] in
 * the same row, centred on the title block) that the [content] scrolls under, and an optional [floatingActionButton].
 * The navigation bar belongs to the shell; window insets too: the frame pads none. [content] gets the padding to apply.
 * With [onTitleClick] the title block is a menu (`Сентябрь ▾`, the schedule's month sheet) that TalkBack reads as
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
    ) { padding ->
        // The body starts at the header's edge and is clipped there: nothing scrolled, stretched or shadowed shows
        // above it. The content gets the rest of the padding (the FAB's and the bar's room).
        val layout = LocalLayoutDirection.current
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).clipToBounds()) {
            content(
                PaddingValues(
                    start = padding.calculateStartPadding(layout),
                    end = padding.calculateEndPadding(layout),
                    bottom = padding.calculateBottomPadding(),
                ),
            )
        }
    }
}

/**
 * One line (owner, 2026-10-13, header lab variant 7): the title and the date on one baseline, 56 dp. The date goes
 * under the title when both do not fit; the title never truncates, so the header grows instead. It never collapses.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val spacing = ItmoTheme.spacing
    Surface(color = colors.surface, modifier = Modifier.testTag(ItmoScreenTags.ROOT_HEADER)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = RootHeaderHeight)
                .padding(start = spacing.screenMargin, end = spacing.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val menu = if (onTitleClick == null) {
                Modifier
            } else {
                Modifier.clip(ItmoTheme.shapes.medium).clickable(onClickLabel = titleClickLabel, role = Role.Button, onClick = onTitleClick)
            }
            FlowRow(
                Modifier
                    .weight(1f)
                    .then(menu)
                    .padding(vertical = spacing.compact)
                    .semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(spacing.compact),
            ) {
                Row(Modifier.alignByBaseline(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = colors.onSurface, style = type.titleLarge)
                    if (onTitleClick != null) {
                        Icon(painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null, tint = colors.onSurface)
                    }
                }
                if (subtitle != null) {
                    Text(subtitle, Modifier.alignByBaseline(), color = colors.onSurfaceVariant, style = type.bodyMedium)
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
 * A pushed screen (subject, person, a settings page): the back button, the title and its optional [subtitle] as one
 * paragraph that wraps in full, [actions], no navigation bar; the header is static and grows with the title.
 * [backLabel] is what TalkBack reads for the back button.
 */
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
    Scaffold(
        modifier = modifier,
        topBar = {
            DetailHeader(title, subtitle, actions) {
                HeaderAction(painterResource(Res.drawable.ic_arrow_back), backLabel, onBack)
            }
        },
        floatingActionButton = floatingActionButton,
        containerColor = ItmoTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        content = content,
    )
}

/** Header lab variant 7 on a pushed screen: back, the title with the subtitle flowing after it, the actions. */
@Composable
private fun DetailHeader(
    title: String,
    subtitle: String?,
    actions: @Composable RowScope.() -> Unit,
    navigation: @Composable () -> Unit,
) {
    val type = ItmoTheme.typography
    val colors = ItmoTheme.colorScheme
    val spacing = ItmoTheme.spacing
    val text = buildAnnotatedString {
        append(title)
        if (!subtitle.isNullOrEmpty()) {
            append("  ")
            val quiet = SpanStyle(
                color = colors.onSurfaceVariant,
                fontSize = type.bodyMedium.fontSize,
                fontWeight = type.bodyMedium.fontWeight,
            )
            withStyle(quiet) { append(subtitle) }
        }
    }
    Surface(color = colors.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = DetailHeaderHeight)
                .padding(horizontal = spacing.related, vertical = spacing.compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation()
            if (title.isEmpty()) {
                // Nothing while the title is not known yet (loading, error): an empty text is an unlabelled node.
                Spacer(Modifier.weight(1f))
            } else {
                Text(
                    text,
                    Modifier.weight(1f).padding(start = spacing.related).semantics { heading() },
                    color = colors.onSurface,
                    style = type.titleMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** The root header's height in one line. */
private val RootHeaderHeight = 56.dp

/** The pushed screen's header height in one line, Material's small top app bar. */
private val DetailHeaderHeight = 64.dp
