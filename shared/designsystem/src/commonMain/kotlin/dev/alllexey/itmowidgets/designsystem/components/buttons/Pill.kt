package dev.alllexey.itmowidgets.designsystem.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/**
 * A one-line label in a small container. Without [tone] it is the neutral badge of an own item (`моя`) or a count, on
 * `secondaryContainer`; with [tone] it is a status (`На проверке`, `Отклонён`) in that colour over a 12 % wash of it,
 * fully rounded, since content-based palettes make the tone's container too dark for the tone as text. Under the iOS
 * style both are capsules in the iOS type.
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val capsule = tone != null || ItmoTheme.platformStyle == ItmoPlatformStyle.Ios
    val shape = if (capsule) ItmoTheme.shapes.full else ItmoTheme.shapes.small
    val container = tone?.copy(alpha = STATUS_WASH_ALPHA) ?: ItmoTheme.colorScheme.secondaryContainer
    Text(
        text,
        modifier
            .background(container, shape)
            .padding(if (tone == null) BadgePadding else StatusPadding),
        color = tone ?: ItmoTheme.colorScheme.onSecondaryContainer,
        style = ItmoTheme.typography.labelMedium,
        maxLines = 1,
    )
}

/**
 * A decorative 10 dp dot in [tone], such as a teacher's review tone beside the words that say it. Without a tone it
 * stays as an empty slot of the same size, so the row does not shift when the tone arrives.
 */
@Composable
fun ToneDot(
    tone: Color?,
    modifier: Modifier = Modifier,
) {
    val dot = modifier.size(DotSize)
    Box(if (tone == null) dot else dot.background(tone, CircleShape))
}

/** `ReviewViews.kt`'s `PILL_ALPHA` (31 of 255). */
private const val STATUS_WASH_ALPHA = 31f / 255f

/** `bg_link_own_badge` and `bg_home_badge` rows: 8 dp by 2 dp. */
private val BadgePadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

/** `item_own_teacher_review.xml`'s status: 10 dp by 2 dp. */
private val StatusPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)

/** `bg_teacher_level_dot`. */
private val DotSize = 10.dp
