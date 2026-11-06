package dev.alllexey.itmowidgets.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackUrlTest {

    @Test
    fun theLinkCarriesThePlatformVersionAndEncodedDevice() {
        assertEquals(
            "https://fb.widgets.alllexey.dev/?platform=android&version=2.3.0-beta.1&device=Google%20Pixel%207",
            ProjectLinks.feedbackUrl(FeedbackPlatform.ANDROID, "2.3.0-beta.1", " Google Pixel 7 "),
        )
        assertEquals(
            "https://fb.widgets.alllexey.dev/?platform=ios&version=2.3&device=iPhone17%2C1",
            ProjectLinks.feedbackUrl(FeedbackPlatform.IOS, "2.3", "iPhone17,1"),
        )
    }

    @Test
    fun blankValuesAreLeftOutAndLongOnesTrimmedToTheSiteLimits() {
        assertEquals("https://fb.widgets.alllexey.dev/?platform=ios", ProjectLinks.feedbackUrl(FeedbackPlatform.IOS, " ", ""))
        val url = ProjectLinks.feedbackUrl(FeedbackPlatform.ANDROID, "1".repeat(50), "d".repeat(130))
        assertEquals("https://fb.widgets.alllexey.dev/?platform=android&version=${"1".repeat(40)}&device=${"d".repeat(120)}", url)
    }
}
