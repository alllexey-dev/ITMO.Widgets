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

    /** A filled button, a capsule: `UIButton.Configuration.filled()` at `.medium` size, one line. */
    val buttonHeight: Dp = 34.33.dp

    /** A large filled button, a capsule: the same at `.large` size. */
    val buttonLargeHeight: Dp = 50.33.dp
}
