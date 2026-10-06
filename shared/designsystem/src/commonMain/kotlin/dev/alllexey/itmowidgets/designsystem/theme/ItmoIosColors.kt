package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * UIKit's dynamic system colours that the iOS variants of the kit draw with, beside the extended colours: resolved
 * on the pinned simulator (iOS 27.0, DS-IOS-01) with `resolvedColor(with:)` for the light and the dark trait, as
 * `#AARRGGBB`. Separators, secondary labels and fills keep UIKit's alpha. The accent stays `colorScheme.primary`.
 */
@Immutable
data class ItmoIosColors(
    /** `systemGroupedBackground`: behind inset groups. */
    val groupedBackground: Color,
    /** `secondarySystemGroupedBackground`: a cell of an inset group. */
    val groupedCell: Color,
    /** `separator`: between rows. */
    val separator: Color,
    /** `secondaryLabel`: subtitles, values, footers. */
    val secondaryLabel: Color,
    /** `tertiaryLabel`: placeholders, disabled text. */
    val tertiaryLabel: Color,
    /** `systemFill`: a track, an unselected control. */
    val systemFill: Color,
    /** `systemGreen`: a switch that is on, success. */
    val systemGreen: Color,
    /** `systemRed`: destructive actions. */
    val systemRed: Color,
    /** `tertiarySystemFill`: the track of a segmented control (DS-IOS-02, read off its render). */
    val tertiarySystemFill: Color,
    /** The selected segment's thumb of a segmented control: read off its render (DS-IOS-02). */
    val segmentedThumb: Color,
    /** `systemGroupedBackground` at the elevated level: behind the inset groups of a sheet (DS-IOS-03). */
    val groupedSheetBackground: Color,
    /** `secondarySystemGroupedBackground` at the elevated level: a cell of an inset group in a sheet (DS-IOS-03). */
    val groupedSheetCell: Color,
    /** A pressed cell: `UIBackgroundConfiguration.listCell()` highlighted, `systemGray4` at both levels (DS-IOS-03). */
    val cellHighlight: Color,
    /** `label`: an alert's title and its buttons' labels (DS-IOS-05). */
    val label: Color,
    /**
     * The opaque stand-in for the glass of alerts and menus, which the kit does not draw: `systemBackground` in light
     * and the elevated `secondarySystemBackground` in dark. Not measured: the glass has no colour of its own
     * (DS-IOS-05).
     */
    val overlay: Color,
) {
    companion object {
        val Light = ItmoIosColors(
            groupedBackground = Color(0xFFF2F2F7),
            groupedCell = Color(0xFFFFFFFF),
            separator = Color(0x1F3C3C43),
            secondaryLabel = Color(0x993C3C43),
            tertiaryLabel = Color(0x4C3C3C43),
            systemFill = Color(0x33787880),
            systemGreen = Color(0xFF34C759),
            systemRed = Color(0xFFFF383C),
            tertiarySystemFill = Color(0x1F767680),
            segmentedThumb = Color(0xFFFFFFFF),
            groupedSheetBackground = Color(0xFFF2F2F7),
            groupedSheetCell = Color(0xFFFFFFFF),
            cellHighlight = Color(0xFFD1D1D6),
            label = Color(0xFF000000),
            overlay = Color(0xFFFFFFFF),
        )

        val Dark = ItmoIosColors(
            groupedBackground = Color(0xFF000000),
            groupedCell = Color(0xFF1C1C1E),
            separator = Color(0x80545458),
            secondaryLabel = Color(0x99EBEBF5),
            tertiaryLabel = Color(0x4CEBEBF5),
            systemFill = Color(0x5C787880),
            systemGreen = Color(0xFF30D158),
            systemRed = Color(0xFFFF4245),
            tertiarySystemFill = Color(0x3D767680),
            segmentedThumb = Color(0xFF5A5A5E),
            groupedSheetBackground = Color(0xFF1C1C1E),
            groupedSheetCell = Color(0xFF2C2C2E),
            cellHighlight = Color(0xFF3A3A3C),
            label = Color(0xFFFFFFFF),
            overlay = Color(0xFF2C2C2E),
        )

        fun of(dark: Boolean): ItmoIosColors = if (dark) Dark else Light
    }
}

internal val LocalItmoIosColors = staticCompositionLocalOf { ItmoIosColors.Light }
