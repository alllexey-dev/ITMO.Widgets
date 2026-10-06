package dev.alllexey.itmowidgets.designsystem.components.groups

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme

/** What a list preview stands on under the iOS style. */
internal enum class PreviewBackdrop {
    /** `systemGroupedBackground`, behind a screen's inset groups. */
    Screen,

    /** The elevated grouped background of a sheet. */
    Sheet,

    /** A cell of an inset group, for a piece that lives inside a row. */
    Cell,
}

/**
 * Previews only: under the iOS style the list previews stand on UIKit's grouped background, as an iOS screen draws
 * them; under Material the preview keeps the scheme's background, so its baselines do not change.
 */
@Composable
internal fun Modifier.groupPreviewBackdrop(backdrop: PreviewBackdrop = PreviewBackdrop.Screen): Modifier {
    if (ItmoTheme.platformStyle == ItmoPlatformStyle.Material) return this
    val colors = ItmoTheme.iosColors
    val color = when (backdrop) {
        PreviewBackdrop.Screen -> colors.groupedBackground
        PreviewBackdrop.Sheet -> colors.groupedSheetBackground
        PreviewBackdrop.Cell -> colors.groupedCell
    }
    return background(color)
}
