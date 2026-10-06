package dev.alllexey.itmowidgets.feature.update.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppVersionNameTest {

    @Test
    fun ordersReleasesByNumberInsteadOfByText() {
        assertTrue(AppVersionName("2.10") > AppVersionName("2.9"))
        assertTrue(AppVersionName("2.1.3") > AppVersionName("2.1"))
        assertTrue(AppVersionName("3.0") > AppVersionName("2.99.99"))
    }

    @Test
    fun aPreReleaseSortsBelowTheReleaseItLeadsTo() {
        assertTrue(AppVersionName("2.1-SNAPSHOT") < AppVersionName("2.1"))
        assertTrue(AppVersionName("2.1-SNAPSHOT") < AppVersionName("2.2"))
        assertTrue(AppVersionName("2.1-rc1") < AppVersionName("2.1-rc2"))
    }

    @Test
    fun missingAndNonNumericSegmentsCountAsZero() {
        assertEquals(0, AppVersionName("2.1").compareTo(AppVersionName("2.1.0")))
        assertEquals(0, AppVersionName("2.1").compareTo(AppVersionName("2.1.x")))
        assertTrue(AppVersionName("2.1.1") > AppVersionName("2.1.x"))
    }

    @Test
    fun keepsTheReportedNameForDisplayAndStorage() {
        assertEquals("2.1-SNAPSHOT", AppVersionName("2.1-SNAPSHOT").raw)
    }

    @Test
    fun ordersThe22PatchLine23DevelopmentBetasUpTo9And23Releases() {
        // Suffixes compare as text, so beta.10 would sort below beta.9: betas stop at 9.
        val order = listOf("2.2.9", "2.3-SNAPSHOT", "2.3.0-beta.1", "2.3.0-beta.9", "2.3", "2.3.1").map(::AppVersionName)
        order.zipWithNext().forEach { (lower, higher) -> assertTrue(lower < higher, "$lower < $higher") }
    }
}
