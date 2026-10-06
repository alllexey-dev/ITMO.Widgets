package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.components.bars.IOS_PRESSED_ALPHA
import dev.alllexey.itmowidgets.designsystem.components.controls.ItmoActivityIndicator
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.designsystem.tokens.IosMetrics

/** The four Material button emphases of `docs/design.md` § Icons, actions and selection. */
enum class ProgressButtonStyle {
    /** The strong primary action. */
    Filled,

    /** An ordinary prominent action. */
    Tonal,

    /** A secondary or contextual action with an outline. */
    Outlined,

    /** A secondary or contextual action without a container. */
    Text,
}

/**
 * A Material button that shows its own progress: while [inProgress] a small indeterminate indicator in the button's
 * content colour takes the icon's place (or covers the label of a button without an icon), so the button keeps its
 * size, TalkBack keeps reading [label], and a second tap does nothing. Replaces `core/ui/ButtonProgress.kt`.
 *
 * Under the iOS style it is a `UIButton` capsule of the medium size: [ProgressButtonStyle.Filled] is the prominent
 * one in the tint, [ProgressButtonStyle.Tonal] and [ProgressButtonStyle.Outlined] are bordered (the tint over an
 * 18 % fill of it), [ProgressButtonStyle.Text] is the plain tint label. A press dims it instead of a ripple, and the
 * progress is the medium [ItmoActivityIndicator] in the content colour, with the same geometry rules.
 */
@Composable
fun ProgressButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ProgressButtonStyle = ProgressButtonStyle.Filled,
    inProgress: Boolean = false,
    enabled: Boolean = true,
    icon: Painter? = null,
) {
    val click = { if (!inProgress) onClick() }
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Ios) {
        IosButton(label, click, modifier, style, inProgress, enabled, icon)
        return
    }
    val padding = contentPadding(style, withIcon = icon != null)
    val content: @Composable () -> Unit = { ProgressButtonContent(label, icon, inProgress) }
    // Material's buttons draw 40 dp and reserve a 48 dp touch target around it, as the XML buttons' insets do.
    when (style) {
        ProgressButtonStyle.Filled -> Button(click, modifier, enabled, contentPadding = padding) { content() }
        ProgressButtonStyle.Tonal -> FilledTonalButton(click, modifier, enabled, contentPadding = padding) { content() }
        ProgressButtonStyle.Outlined -> OutlinedButton(click, modifier, enabled, contentPadding = padding) { content() }
        ProgressButtonStyle.Text -> TextButton(click, modifier, enabled, contentPadding = padding) { content() }
    }
}

/** `Widget.Material3.Button` pads 24 dp (16 dp before an icon), `.TextButton` 12 dp (16 dp after an icon). */
private fun contentPadding(style: ProgressButtonStyle, withIcon: Boolean): PaddingValues = when {
    style == ProgressButtonStyle.Text && withIcon -> PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
    style == ProgressButtonStyle.Text -> PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    withIcon -> PaddingValues(start = 16.dp, end = 24.dp, top = 6.dp, bottom = 6.dp)
    else -> PaddingValues(horizontal = 24.dp, vertical = 6.dp)
}

@Composable
private fun ProgressButtonContent(label: String, icon: Painter?, inProgress: Boolean) {
    if (icon != null) {
        Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
            if (inProgress) ButtonProgressIndicator() else Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(IconLabelGap))
        Text(label)
    } else {
        // The label stays measured and in the semantics tree; only its pixels give way to the indicator.
        Box(contentAlignment = Alignment.Center) {
            Text(label, Modifier.alpha(if (inProgress) 0f else 1f))
            if (inProgress) ButtonProgressIndicator()
        }
    }
}

@Composable
private fun ButtonProgressIndicator() {
    CircularProgressIndicator(
        Modifier
            .size(IconSize)
            .padding(IndicatorInset),
        color = LocalContentColor.current,
        strokeWidth = IndicatorStroke,
    )
}

@Composable
private fun IosButton(
    label: String,
    click: () -> Unit,
    modifier: Modifier,
    style: ProgressButtonStyle,
    inProgress: Boolean,
    enabled: Boolean,
    icon: Painter?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (container, content) = iosColors(style, enabled)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = click)
            .graphicsLayer { alpha = if (pressed) IOS_PRESSED_ALPHA else 1f }
            .heightIn(min = IosMetrics.buttonHeight)
            .background(container, CircleShape)
            .padding(horizontal = IosMetrics.buttonHorizontalPadding, vertical = IosLabelPadding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides content,
            LocalTextStyle provides ItmoTheme.typography.bodyLarge,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { IosButtonContent(label, icon, inProgress) }
        }
    }
}

@Composable
private fun IosButtonContent(label: String, icon: Painter?, inProgress: Boolean) {
    if (icon != null) {
        Box(Modifier.size(IosMetrics.activityIndicatorMedium), contentAlignment = Alignment.Center) {
            if (inProgress) {
                ItmoActivityIndicator(color = LocalContentColor.current)
            } else {
                Icon(icon, contentDescription = null, Modifier.size(IosMetrics.activityIndicatorMedium))
            }
        }
        Spacer(Modifier.width(IconLabelGap))
        Text(label)
    } else {
        Box(contentAlignment = Alignment.Center) {
            Text(label, Modifier.alpha(if (inProgress) 0f else 1f))
            if (inProgress) ItmoActivityIndicator(color = LocalContentColor.current)
        }
    }
}

/**
 * Container and content of each style, from `UIButton.Configuration` on the pinned runtime (DS-IOS-02): `.filled()`
 * draws the tint under white text, `.tinted()` the tint at 0x2E alpha under tint text, `.plain()` no container. A
 * disabled button takes the tertiary fill and label, as UIKit's disabled configurations do.
 */
@Composable
private fun iosColors(style: ProgressButtonStyle, enabled: Boolean): Pair<Color, Color> {
    val tint = ItmoTheme.colorScheme.primary
    val ios = ItmoTheme.iosColors
    return when {
        !enabled && style == ProgressButtonStyle.Text -> Color.Transparent to ios.tertiaryLabel
        !enabled -> ios.tertiarySystemFill to ios.tertiaryLabel
        style == ProgressButtonStyle.Filled -> tint to ItmoTheme.colorScheme.onPrimary
        style == ProgressButtonStyle.Text -> Color.Transparent to tint
        else -> tint.copy(alpha = IOS_TINTED_FILL_ALPHA) to tint
    }
}

/**
 * `UIButton.Configuration.tinted()`'s background: the tint at alpha 0x2E, measured on the pinned runtime (DS-IOS-02).
 */
private const val IOS_TINTED_FILL_ALPHA = 0x2E / 255f

/**
 * The label's top and bottom inset: UIKit's 7 pt around a 20.33 pt body line, less half of the 1.67 pt the body
 * style's 22 pt line height adds, so one line makes [IosMetrics.buttonHeight] and a larger font grows as UIKit's.
 */
private val IosLabelPadding = 6.dp

/** `Widget.Material3.Button`'s `iconSize`: the slot the View helper's drawable took. */
private val IconSize = 18.dp

/** `m3_comp_button_small_icon_label_space`. */
private val IconLabelGap = 8.dp

/**
 * `Widget.Material3.CircularProgressIndicator.ExtraSmall` (20 dp, 2 dp inset, 2.5 dp track) scaled into the 18 dp
 * icon slot, as the View helper drew it.
 */
private val IndicatorInset = 1.dp
private val IndicatorStroke = 2.dp
