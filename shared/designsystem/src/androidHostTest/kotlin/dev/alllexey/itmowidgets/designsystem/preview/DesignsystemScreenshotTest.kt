package dev.alllexey.itmowidgets.designsystem.preview

import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshots

/**
 * The kit's previews, always in all four Material appearances and the three iOS ones (`verify-quick` checks them on
 * every PR); the Material 3 Expressive kit of `designsystem.kit` is Android's only, so it has no iOS ones (ADR 0031).
 * The week strip keeps 48 dp days at 320 dp that overlap a little, exempt from ATF 48 dp targets as the sport strip.
 */
@PreviewScreenshots(
    packageTree = "dev.alllexey.itmowidgets.designsystem",
    allAppearances = true,
    iosAppearances = true,
    materialOnlyPackageTrees = ["dev.alllexey.itmowidgets.designsystem.kit"],
    narrowTouchTargetExemptions = ["WeekStripPreview"],
)
class DesignsystemScreenshotTest : PreviewScreenshotTest()
