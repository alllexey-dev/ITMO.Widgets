package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

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
