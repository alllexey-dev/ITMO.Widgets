package dev.alllexey.itmowidgets.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class AppBuildTest {

    @Test
    fun theCommitShortensToSevenDigitsAndTheDetailsListOnlyKnownParts() {
        val fork = AppBuild("2.3.0", "20292", AppBuild.CUSTOM, "AlexPer777/ITMO.Widgets", "a1b2c3d4e5f6-dirty")

        assertEquals("a1b2c3d-dirty", fork.shortCommit)
        assertEquals(
            "2.3.0 (20292); channel=custom; source=AlexPer777/ITMO.Widgets; commit=a1b2c3d4e5f6-dirty",
            fork.details()
        )
        assertEquals("2.3.0 (20292); channel=play", AppBuild("2.3.0", "20292", "play").details())
    }
}
