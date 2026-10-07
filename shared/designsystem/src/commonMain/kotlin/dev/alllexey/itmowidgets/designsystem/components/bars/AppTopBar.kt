package dev.alllexey.itmowidgets.designsystem.components.bars

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics
import dev.alllexey.itmowidgets.shared.designsystem.Res
import dev.alllexey.itmowidgets.shared.designsystem.ic_arrow_back
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_left
import org.jetbrains.compose.resources.painterResource

/**
 * The small top bar of a contextual screen: an optional navigation button (back or close), a concise title of up to
 * two lines and trailing actions, on `surface`. An optional [subtitle] (the My ITMO page's host, as the toolbar
 * subtitle showed it in 2.2) takes one ellipsized line under the title, which then keeps to one line. It draws no
 * window insets; the host pads the system bars, or the caller adds `windowInsetsPadding` to [modifier].
 *
 * Under the iOS style it is the inline navigation bar: the title centred in one line of headline (the subtitle centred
 * under it), the navigation button leading and the actions trailing in the tint, no tonal elevation, and a hairline
 * separator when [scrolledUnder] says content scrolls beneath it (Material's bar ignores it). The swipe-back gesture
 * stays the host's.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrolledUnder: Boolean = false,
    subtitle: String? = null,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> MaterialTopBar(title, subtitle, modifier, navigation, actions)
        ItmoPlatformStyle.Ios -> IosTopBar(title, subtitle, modifier, navigation, actions, scrolledUnder)
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
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> IconButton(onClick = onClick, modifier = modifier) {
            Icon(icon, contentDescription = label, tint = ItmoTheme.colorScheme.onSurface)
        }
        ItmoPlatformStyle.Ios -> IosBarButton(onClick, modifier) {
            Icon(icon, contentDescription = label, tint = ItmoTheme.colorScheme.primary)
        }
    }
}

/**
 * A trailing action of [AppTopBar] named by a word ("Готово", "Изменить"): a text button under Material, the tint's
 * body text in a 44 pt target under the iOS style.
 */
@Composable
fun AppTopBarTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> TextButton(onClick = onClick, modifier = modifier) { Text(label) }
        ItmoPlatformStyle.Ios -> IosBarButton(onClick, modifier) {
            Text(
                label,
                Modifier.padding(horizontal = IosTextActionPadding),
                color = ItmoTheme.colorScheme.primary,
                style = ItmoTheme.typography.bodyLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * The back button of [AppTopBar], read as [label]. Under Material it is the back arrow, like an [AppTopBarAction];
 * under the iOS style the back chevron in the tint, followed by [title] (the previous screen's title, when the caller
 * has one) in body text. [title] is the caller's text, never the kit's.
 */
@Composable
fun AppTopBarBack(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    when (ItmoTheme.platformStyle) {
        ItmoPlatformStyle.Material -> AppTopBarAction(painterResource(Res.drawable.ic_arrow_back), label, onClick, modifier)
        ItmoPlatformStyle.Ios -> IosBarButton(onClick, modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.ic_chevron_left),
                    contentDescription = label,
                    Modifier.size(BackChevronSize),
                    tint = ItmoTheme.colorScheme.primary,
                )
                if (title != null) {
                    Text(
                        title,
                        Modifier
                            .padding(end = IosTextActionPadding)
                            .clearAndSetSemantics {},
                        color = ItmoTheme.colorScheme.primary,
                        style = ItmoTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialTopBar(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    navigation: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), color = ItmoTheme.colorScheme.surface) {
        Row(
            Modifier
                .heightIn(min = BarHeight)
                .padding(horizontal = BarEdgePadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Column(
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (navigation == null) TitleEdgeInset else TitleNavigationGap,
                        end = TitleEdgeInset,
                    ),
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    color = ItmoTheme.colorScheme.onSurface,
                    style = ItmoTheme.typography.titleLarge,
                    maxLines = if (subtitle == null) TITLE_MAX_LINES else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Subtitle(subtitle)
            }
            actions()
        }
    }
}

/**
 * `UINavigationBar` inline: [IosMetrics.navigationBarHeight] high, bar buttons [IosMetrics.barEdgeInset] from the
 * edges, the title in headline centred on the bar and kept clear of the wider side.
 */
@Composable
private fun IosTopBar(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    navigation: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    scrolledUnder: Boolean,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ItmoTheme.colorScheme.surface),
    ) {
        Layout(
            contents = listOf(
                { navigation?.invoke() },
                { Row(verticalAlignment = Alignment.CenterVertically) { actions() } },
                {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            title,
                            Modifier.semantics { heading() },
                            color = ItmoTheme.colorScheme.onSurface,
                            style = ItmoTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) Subtitle(subtitle)
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = IosMetrics.navigationBarHeight)
                .padding(horizontal = IosMetrics.barEdgeInset),
        ) { (leading, trailing, heading), constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val lead = leading.firstOrNull()?.measure(loose)
            val trail = trailing.first().measure(loose)
            val side = maxOf(lead?.width ?: 0, trail.width)
            val gap = IosTitleGap.roundToPx()
            val room = (constraints.maxWidth - 2 * (side + gap)).coerceAtLeast(0)
            val text = heading.first().measure(Constraints(maxWidth = room))
            val height = maxOf(constraints.minHeight, lead?.height ?: 0, trail.height, text.height)
            layout(constraints.maxWidth, height) {
                lead?.placeRelative(0, (height - lead.height) / 2)
                trail.placeRelative(constraints.maxWidth - trail.width, (height - trail.height) / 2)
                text.placeRelative((constraints.maxWidth - text.width) / 2, (height - text.height) / 2)
            }
        }
        if (scrolledUnder) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(IosMetrics.separatorThickness)
                    .background(ItmoTheme.iosColors.separator),
            )
        }
    }
}

/** The one line under the title: `TextAppearance.Material3.BodySmall` in `colorOnSurfaceVariant`, as in 2.2. */
@Composable
private fun Subtitle(text: String) {
    Text(
        text,
        color = ItmoTheme.colorScheme.onSurfaceVariant,
        style = ItmoTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A bar button: [content] in the tint, centred in a [IosMetrics.barButtonSize] target, dimmed while pressed instead of
 * a ripple. Shared with the floating toolbar's iOS capsule.
 */
@Composable
internal fun IosBarButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .sizeIn(minWidth = IosMetrics.barButtonSize, minHeight = IosMetrics.barButtonSize)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (pressed) IOS_PRESSED_ALPHA else 1f },
        contentAlignment = Alignment.Center,
    ) { content() }
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

/** The least room between the iOS title and the wider side's buttons. */
private val IosTitleGap = 8.dp

/** A text bar button's inset inside its target. */
private val IosTextActionPadding = 6.dp

/**
 * The Material Symbols chevron at the size whose glyph (half the box high) matches the bar's `chevron.backward`
 * symbol, a 16 by 23 pt image (DS-IOS-02); glyph-exact checks are DS-IOS-06's.
 */
private val BackChevronSize = 32.dp

/** How much a pressed iOS button dims: UIKit's highlighted bar buttons and plain buttons fade instead of a ripple. */
internal const val IOS_PRESSED_ALPHA = 0.5f
