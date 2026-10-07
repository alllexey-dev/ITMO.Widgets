package dev.alllexey.itmowidgets.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppBundleVersionTest {

    @Test
    fun readsTheExpandedMarketingVersion() {
        assertEquals("2.3", AppBundleVersion.from("2.3"))
    }

    @Test
    fun aBundleWithoutAVersionReadsAsMissing() {
        assertEquals(AppBundleVersion.MISSING, AppBundleVersion.from(null))
        assertEquals(AppBundleVersion.MISSING, AppBundleVersion.from(" "))
    }

    @Test
    fun anUnexpandedVersionIsABuildError() {
        assertFailsWith<IllegalArgumentException> { AppBundleVersion.from("$(MARKETING_VERSION)") }
    }
}
