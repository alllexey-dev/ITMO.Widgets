package dev.alllexey.itmowidgets.designsystem.preview

import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshots

/**
 * The kit's previews, always in all four appearances (`verify-quick` checks them on every PR).
 * The week strip keeps 48 dp days at 320 dp that overlap a little, exempt from ATF 48 dp targets as the sport strip.
 */
@PreviewScreenshots(
    packageTree = "dev.alllexey.itmowidgets.designsystem",
    allAppearances = true,
    narrowTouchTargetExemptions = ["WeekStripPreview"],
)
class DesignsystemScreenshotTest : PreviewScreenshotTest()
