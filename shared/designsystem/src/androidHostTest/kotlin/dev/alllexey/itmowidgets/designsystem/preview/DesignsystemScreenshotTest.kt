package dev.alllexey.itmowidgets.designsystem.preview

import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshotTest
import dev.alllexey.itmowidgets.testkit.screenshot.PreviewScreenshots

/** The kit's previews, always in all four appearances (`verify-quick` checks them on every PR from DS-02c). */
@PreviewScreenshots(packageTree = "dev.alllexey.itmowidgets.designsystem", allAppearances = true)
class DesignsystemScreenshotTest : PreviewScreenshotTest()
