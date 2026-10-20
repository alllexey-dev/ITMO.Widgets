package dev.alllexey.itmowidgets.designsystem.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** The two spinner sizes: [Medium] inside buttons and rows, [Large] for a section. */
enum class ItmoActivityIndicatorSize(internal val size: Dp) {
    Medium(20.dp),
    Large(37.dp),
}

/**
 * An indeterminate circular indicator of a fixed [size] in `primary` (or [color]). TalkBack reads it as an
 * indeterminate progress.
 */
@Composable
fun ItmoActivityIndicator(
    modifier: Modifier = Modifier,
    size: ItmoActivityIndicatorSize = ItmoActivityIndicatorSize.Medium,
    color: Color = Color.Unspecified,
) {
    CircularProgressIndicator(
        modifier.size(size.size),
        color = color.takeOrElse { ItmoTheme.colorScheme.primary },
        strokeWidth = size.size * MATERIAL_STROKE_FRACTION,
    )
}

/** The Material indicator's track at the width of `ProgressButton`'s, 2 dp in an 18 dp slot. */
private const val MATERIAL_STROKE_FRACTION = 0.1f
