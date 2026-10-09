package dev.alllexey.itmowidgets.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class IosClientVersionTest {

    @Test
    fun aReleaseBuildIsTheAppStoreDistribution() {
        val version = IosClientVersion.from("2.3.0", "20291", debug = false)

        assertEquals("2.3.0 (20291); ios; appstore", version.headerValue)
    }

    @Test
    fun aDebugBuildIsTheDevDistribution() {
        val version = IosClientVersion.from("2.3.0", "20291", debug = true)

        assertEquals("2.3.0 (20291); ios; dev", version.headerValue)
    }

    @Test
    fun aBundleWithoutVersionsReadsAsMissing() {
        val version = IosClientVersion.from(null, " ", debug = true)

        assertEquals("0 (0); ios; dev", version.headerValue)
    }
}
