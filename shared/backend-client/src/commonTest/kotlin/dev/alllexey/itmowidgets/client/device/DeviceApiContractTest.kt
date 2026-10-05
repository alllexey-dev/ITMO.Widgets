package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Port of Core 1.7.0 `DeviceApiContractTest`. The Retrofit reflection check (`@HTTP(method = "DELETE", hasBody =
 * true)` with one `@Body`) becomes an assertion over the request the route case records.
 */
class DeviceApiContractTest {

    @Test
    fun unregisterCurrentDeviceUsesADeleteRequestBody() = runSuspend {
        val request = DeviceRouteCases.unregisterCurrent.record()

        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/device/current", request.path)
        assertEquals(emptyList(), request.query)
        assertEquals("""{"fcmToken":"synthetic-fcm-token"}""", request.body)
    }

    @Test
    fun unregisterRequestKeepsTheWireFieldName() {
        val request = UnregisterDeviceRequest("test-token")

        val json = BackendJson.encodeToString(UnregisterDeviceRequest.serializer(), request)

        assertEquals("{\"fcmToken\":\"test-token\"}", json)
    }
}
