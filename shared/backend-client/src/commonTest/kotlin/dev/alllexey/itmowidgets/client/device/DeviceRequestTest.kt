package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.contract.VendoredContract
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path, query and body of every [DeviceApi] call ([DeviceRouteCases]). */
class DeviceRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(2, DeviceRouteCases.all.size)
        assertEquals(DeviceRouteCases.all.size, DeviceRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun register() = runSuspend {
        DeviceRouteCases.register.assertRequest(
            HttpMethod.Post,
            "/api/device/register-device",
            body = """{"fcmToken":"synthetic-fcm-token","deviceName":"Pixel 8 (synthetic)"}""",
        )
    }

    @Test
    fun unregisterCurrent() = runSuspend {
        DeviceRouteCases.unregisterCurrent.assertRequest(
            HttpMethod.Delete,
            "/api/device/current",
            body = """{"fcmToken":"synthetic-fcm-token"}""",
        )
    }

    @Test
    fun bodiesEqualTheVendoredRequestFixtures() = runSuspend {
        assertJsonEquals(
            VendoredContract.read("requests/RegisterDeviceRequest.json"),
            DeviceRouteCases.register.record().body.orEmpty(),
        )
        assertJsonEquals(
            VendoredContract.read("requests/UnregisterDeviceRequest.json"),
            DeviceRouteCases.unregisterCurrent.record().body.orEmpty(),
        )
    }

    @Test
    fun registerWithoutTheOptionalFieldsSendsExactlyWhatReleasedClientsSend() = runSuspend {
        val body = DeviceRouteCases.register.record().body.orEmpty()

        assertEquals("""{"fcmToken":"synthetic-fcm-token","deviceName":"Pixel 8 (synthetic)"}""", body)
    }

    @Test
    fun registerSendsEveryOptionalFieldThatIsSet() = runSuspend {
        val request = RegisterDeviceRequest(
            fcmToken = DeviceRouteCases.TOKEN,
            deviceName = "iPhone (synthetic)",
            platform = DevicePlatform.IOS,
            alertsAllowed = false,
            appVersion = "2.3",
        )

        val body = recordRequest("register") { client.device.register(request) }.body.orEmpty()

        assertJsonEquals(
            """{"fcmToken":"synthetic-fcm-token","deviceName":"iPhone (synthetic)","platform":"IOS",""" +
                """"alertsAllowed":false,"appVersion":"2.3"}""",
            body,
        )
    }

    @Test
    fun eachOptionalFieldIsOmittedOnItsOwnWhenNull() = runSuspend {
        val full = RegisterDeviceRequest(
            DeviceRouteCases.TOKEN,
            DeviceRouteCases.DEVICE_NAME,
            platform = DevicePlatform.ANDROID,
            alertsAllowed = true,
            appVersion = "2.3",
        )
        val cases = mapOf(
            "platform" to full.copy(platform = null),
            "alertsAllowed" to full.copy(alertsAllowed = null),
            "appVersion" to full.copy(appVersion = null),
        )
        for ((omitted, request) in cases) {
            val body = recordRequest("register") { client.device.register(request) }.body.orEmpty()

            val keys = Regex(""""(\w+)":""").findAll(body).map { it.groupValues[1] }.toSet()
            assertEquals(
                setOf("fcmToken", "deviceName", "platform", "alertsAllowed", "appVersion") - omitted,
                keys,
                omitted,
            )
        }
    }

    @Test
    fun requestsDoNotPrintTheToken() {
        assertEquals(
            "RegisterDeviceRequest(fcmToken=<redacted>, deviceName=Pixel 8 (synthetic), platform=null, " +
                "alertsAllowed=null, appVersion=null)",
            DeviceRouteCases.released.toString(),
        )
        assertEquals(
            "UnregisterDeviceRequest(fcmToken=<redacted>)",
            UnregisterDeviceRequest(DeviceRouteCases.TOKEN).toString(),
        )
    }
}
