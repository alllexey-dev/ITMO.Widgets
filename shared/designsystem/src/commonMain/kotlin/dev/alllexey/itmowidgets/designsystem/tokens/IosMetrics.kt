package dev.alllexey.itmowidgets.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UIKit's metrics that the iOS variants of the kit draw to and that have no Material slot in [ItmoSpacing] or
 * [ItmoShapes]. Measured once on the pinned simulator (iPhone 17, iOS 27.0, `scripts/ios/env.sh`; DS-IOS-01) from
 * UIKit reference views at the default Dynamic Type size; the source of each value is in its comment. One look for
 * every supported iOS: the pinned runtime's. Values in pt, which are dp here.
 */
object IosMetrics {
    /** One line of body text in an inset-grouped row: `UITableViewCell` with its default content configuration. */
    val rowMinHeight: Dp = 53.dp

    /** The leading and trailing padding inside a row: the cell's `layoutMargins`. */
    val rowHorizontalPadding: Dp = 16.dp

    /** The top and bottom padding of a row that grows past [rowMinHeight]: the cell's `layoutMargins`. */
    val rowVerticalPadding: Dp = 15.dp

    /** An inset group's distance from the screen edge: the cell's x in the table, the table's `layoutMargins`. */
    val insetGroupMargin: Dp = 20.dp

    /** The corner radius of an inset group: the first cell's `cornerConfiguration`, `.fixed(26)`. */
    val insetGroupRadius: Dp = 26.dp

    /** A separator's leading inset in a row of text: `UITableViewCell.separatorInset.left`. */
    val separatorInset: Dp = 16.dp

    /** A separator's leading inset in a row with a leading icon: `separatorInset.left` with an image (to the text). */
    val separatorInsetWithIcon: Dp = 56.dp

    /** A separator's trailing inset: `separatorInset.right`; the line stops short of the trailing edge. */
    val separatorTrailingInset: Dp = 16.dp

    /** A separator's thickness: `_UITableViewCellSeparatorView`'s height, three device pixels at 3x. */
    val separatorThickness: Dp = 1.dp

    /**
     * The space above a section header's text in an inset-grouped list: the label's y in the header view
     * (`UIListContentConfiguration.groupedHeader()`, 17 pt semibold in `secondaryLabel`, DS-IOS-03).
     */
    val sectionHeaderTop: Dp = 28.67.dp

    /** The space between a section header's text and its group: the header view's bottom less the label's. */
    val sectionHeaderBottom: Dp = 6.33.dp

    /**
     * The space between a group and its footer's text: the label's y in the footer view
     * (`UIListContentConfiguration.groupedFooter()`, 13 pt regular in `secondaryLabel`).
     */
    val sectionFooterTop: Dp = 7.67.dp

    /** The disclosure indicator (`accessoryType = .disclosureIndicator`): its image view's frame, 10.33 x 14. */
    val disclosureWidth: Dp = 10.33.dp
    val disclosureHeight: Dp = 14.dp

    /** The disclosure indicator's distance from the cell's trailing edge: the accessory's frame in the cell. */
    val disclosureTrailingInset: Dp = 20.dp

    /** The checkmark (`accessoryType = .checkmark`), in the tint: its image view's frame, 19 x 17.33. */
    val checkmarkWidth: Dp = 19.dp
    val checkmarkHeight: Dp = 17.33.dp

    /** The checkmark's distance from the cell's trailing edge: the accessory's frame in the cell. */
    val checkmarkTrailingInset: Dp = 22.5.dp

    /**
     * The gap between a row's text and its accessory or trailing value: `valueCell()`'s
     * `textToSecondaryTextHorizontalPadding` and the content view's end before an accessory (both 8).
     */
    val accessoryGap: Dp = 8.dp

    /** The top corners of a sheet, medium and large detent: `UIDropShadowView.cornerConfiguration`, `.fixed(38)`. */
    val sheetRadius: Dp = 38.dp

    /** How far a medium-detent sheet floats from the screen's sides: the presented view's x. */
    val sheetFloatingInset: Dp = 8.dp

    /** An alert's and an action sheet's corners: `_UIAlertControllerPhoneTVMacView.layer.cornerRadius`. */
    val alertRadius: Dp = 34.dp

    /** An alert's width on a phone: the alert view's frame. */
    val alertWidth: Dp = 320.dp

    /** A button of an alert, a capsule: the action view's frame and corner radius 24. */
    val alertButtonHeight: Dp = 48.dp

    /**
     * A pull-down menu's corners. Not measurable on the simulator, which draws the menu's glass platter without its
     * shape; the inset group's radius until DS-IOS-04 checks it against a device screenshot.
     */
    val menuRadius: Dp = insetGroupRadius

    /** One row of a pull-down menu: `_UIContextMenuCell`'s height. */
    val menuRowHeight: Dp = 42.dp

    /** A pull-down menu's width: `_UIContextMenuView`'s frame. */
    val menuWidth: Dp = 250.dp

    /** A segmented control, a capsule: `UISegmentedControl.intrinsicContentSize.height`. */
    val segmentedHeight: Dp = 31.dp

    /** The gap around the selected segment's capsule: its 27 pt thumb inside the 31 pt control. */
    val segmentedThumbInset: Dp = 2.dp

    /** A switch: `UISwitch`'s frame after `sizeToFit()`, a capsule track (DS-IOS-02). */
    val switchWidth: Dp = 63.dp

    /** A switch's height: `UISwitch`'s frame; the track's corner radius is half of it. */
    val switchHeight: Dp = 28.dp

    /** A switch's thumb, a capsule: `_UILiquidLensView`'s frame inside the switch. */
    val switchThumbWidth: Dp = 37.dp

    /** The thumb's height: the same frame. */
    val switchThumbHeight: Dp = 24.dp

    /** The gap around the thumb: its x when off (2) and its trailing gap when on (63 - 24 - 37). */
    val switchThumbInset: Dp = 2.dp

    /** The spinner of `UIActivityIndicatorView(style: .medium)`: its intrinsic size (DS-IOS-02). */
    val activityIndicatorMedium: Dp = 20.dp

    /** The spinner of `UIActivityIndicatorView(style: .large)`: its intrinsic size. */
    val activityIndicatorLarge: Dp = 37.dp

    /**
     * The spinner's spokes as fractions of its size, read off a 3x render of both sizes: eight round-capped spokes from
     * 0.16 to 0.48 of the size away from the centre (medium 3.3 to 9.9 pt, large 5.5 to 17.5 pt), 0.13 of the size wide
     * (medium 2.5 pt, large 5 pt).
     */
    const val activityIndicatorSpokeInner: Float = 0.16f
    const val activityIndicatorSpokeOuter: Float = 0.48f
    const val activityIndicatorSpokeWidth: Float = 0.13f

    /**
     * The opacity of each spoke behind the leading one, which turns clockwise: the render's grey levels over the
     * secondary label colour (0.85, 0.71, 0.56, 0.42, then 0.27 for the other four).
     */
    val activityIndicatorSpokeAlphas: List<Float> = listOf(0.85f, 0.71f, 0.56f, 0.42f, 0.27f, 0.27f, 0.27f, 0.27f)

    /** A button's leading and trailing content inset: `UIButton.Configuration.contentInsets` at `.medium`. */
    val buttonHorizontalPadding: Dp = 12.dp

    /** A button's top and bottom content inset: the same insets; one body line (20.33) makes [buttonHeight]. */
    val buttonVerticalPadding: Dp = 7.dp

    /**
     * An inline navigation bar on iOS 26 and later: `UINavigationBar`'s height with `prefersLargeTitles` off (the
     * classic 44 grew with the Liquid Glass bar).
     */
    val navigationBarHeight: Dp = 54.dp

    /** A bar button item's frame (`UIPlatformGlassInteractionView`), back included. */
    val barButtonSize: Dp = 44.dp

    /** The first and the last bar button's distance from the screen edge: their x in the bar. */
    val barEdgeInset: Dp = 16.dp

    /** A filled button, a capsule: `UIButton.Configuration.filled()` at `.medium` size, one line. */
    val buttonHeight: Dp = 34.33.dp

    /** A large filled button, a capsule: the same at `.large` size. */
    val buttonLargeHeight: Dp = 50.33.dp
}
