package dev.alllexey.itmowidgets.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class IosClientVersionTest {

    @Test
    fun theHeaderNamesTheBuildAndItsDistribution() {
        val version = IosClientVersion.from("2.3.0", "20291", distribution = "sideload")

        assertEquals("2.3.0 (20291); ios; sideload", version.headerValue)
    }

    @Test
    fun aBundleWithoutVersionsReadsAsMissing() {
        val version = IosClientVersion.from(null, " ", distribution = "dev")

        assertEquals("0 (0); ios; dev", version.headerValue)
    }

    @Test
    fun aDebugBinaryIsDevWhateverItCarries() {
        assertEquals("dev", IosClientVersion.distribution(debug = true, provisioningProfile = true, sandboxReceipt = true))
    }

    @Test
    fun aReleaseBuildWithItsProvisioningProfileIsSideloaded() {
        assertEquals("sideload", IosClientVersion.distribution(debug = false, provisioningProfile = true, sandboxReceipt = true))
    }

    @Test
    fun aReleaseBuildWithASandboxReceiptIsTestFlight() {
        assertEquals("testflight", IosClientVersion.distribution(debug = false, provisioningProfile = false, sandboxReceipt = true))
    }

    @Test
    fun aReleaseBuildWithNeitherIsTheAppStore() {
        assertEquals("appstore", IosClientVersion.distribution(debug = false, provisioningProfile = false, sandboxReceipt = false))
    }
}
