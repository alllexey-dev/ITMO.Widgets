package dev.alllexey.itmowidgets.client.app

import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test

/**
 * Backend's vendored app fixtures (`claims/app.txt`): both version answers decode through [AppApi] and re-encode to
 * the fixture's `data`. Not claimed: `http/app/latestAppVersion.json`, the legacy route this client does not mirror.
 */
class AppVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/app/appVersionInfo.json", AppVersionInfo.serializer()) { app.versionInfo(null) },
        ResponseClaim("http/app/appVersionInfoIos.json", AppVersionInfo.serializer()) {
            app.versionInfo(DevicePlatform.IOS)
        },
    )

    @Test
    fun everyClaimDecodes() = runSuspend { assertAreaClaims("app", responses, requests = emptyList()) }
}
