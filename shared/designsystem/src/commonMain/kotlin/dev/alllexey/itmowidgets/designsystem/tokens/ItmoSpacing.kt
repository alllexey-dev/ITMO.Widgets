package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing and fixed sizes on the 4 dp grid, one slot per `design_*` dimen of `res/values/dimens.xml` that is not a
 * card shape ([ShapeTokens]); `DesignTokensParityTest` keeps them equal.
 */
@Immutable
data class ItmoSpacing(
    /** Between related rows. */
    val related: Dp,
    /** A compact list gap. */
    val compact: Dp,
    /** Between the parts of one piece of content. */
    val content: Dp,
    /** Between groups. */
    val group: Dp,
    /** Between sections. */
    val section: Dp,
    /** The screen's horizontal margin. */
    val screenMargin: Dp,
    val cardPadding: Dp,
    /** The padding of a large summary card. */
    val summaryPadding: Dp,
    /** The minimum touch target; compactness never shrinks it. */
    val touchTarget: Dp,
    /** Bottom padding of a list under a stack of two FABs, so its last item scrolls clear of them. */
    val fabStackClearance: Dp,
    /** The padding of a full-screen loading, empty or error state. */
    val statePadding: Dp,
    /** The icon of a full-screen state. */
    val stateIcon: Dp,
    /** The icon of an inline state inside a card or sheet. */
    val stateInlineIcon: Dp,
) {
    companion object {
        val Default = ItmoSpacing(
            related = 4.dp,
            compact = 8.dp,
            content = 12.dp,
            group = 16.dp,
            section = 24.dp,
            screenMargin = 16.dp,
            cardPadding = 16.dp,
            summaryPadding = 20.dp,
            touchTarget = 48.dp,
            fabStackClearance = 152.dp,
            statePadding = 32.dp,
            stateIcon = 64.dp,
            stateInlineIcon = 56.dp,
        )
    }
}

internal val LocalItmoSpacing = staticCompositionLocalOf { ItmoSpacing.Default }
