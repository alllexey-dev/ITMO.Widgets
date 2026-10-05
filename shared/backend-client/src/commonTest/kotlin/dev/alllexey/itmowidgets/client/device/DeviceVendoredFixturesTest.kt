package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.contract.RequestClaim
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test

/**
 * Backend's vendored device fixtures (`claims/device.txt`): both answers are accepted by [DeviceApi] and both request
 * bodies round-trip. Arguments do not matter, the fixture is the answer.
 */
class DeviceVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/device/registerDevice.json", null) { device.register(DeviceRouteCases.released) },
        ResponseClaim("http/device/unregisterCurrentDevice.json", null) {
            device.unregisterCurrent(UnregisterDeviceRequest(DeviceRouteCases.TOKEN))
        },
    )

    private val requests = listOf(
        RequestClaim("requests/RegisterDeviceRequest.json", RegisterDeviceRequest.serializer()),
        RequestClaim("requests/UnregisterDeviceRequest.json", UnregisterDeviceRequest.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("device", responses, requests) }
}
