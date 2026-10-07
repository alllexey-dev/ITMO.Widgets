package dev.alllexey.itmowidgets.designsystem.preview

import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshots

/**
 * The kit's previews, always in all four Material appearances and the three iOS ones (`verify-quick` checks them on
 * every PR).
 */
@PreviewScreenshots(packageTree = "dev.alllexey.itmowidgets.designsystem", allAppearances = true, iosAppearances = true)
class DesignsystemScreenshotTest : PreviewScreenshotTest()
