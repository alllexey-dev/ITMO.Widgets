package dev.alllexey.itmowidgets.client.device

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.envelope
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** What every [DeviceApi] call accepts as Backend's answer, and the strict [DevicePlatform]. */
class DeviceDecodeTest {

    @Test
    fun registerIgnoresTheConfirmationText() = runSuspend {
        assertAnswersNeedNoData("\"Device registered successfully.\"") { device.register(DeviceRouteCases.released) }
    }

    @Test
    fun unregisterCurrentIgnoresTheConfirmationText() = runSuspend {
        assertAnswersNeedNoData("\"Device unregistered successfully.\"") {
            device.unregisterCurrent(UnregisterDeviceRequest(DeviceRouteCases.TOKEN))
        }
    }

    @Test
    fun platformIsStrictAndCaseSensitive() {
        for (platform in DevicePlatform.entries) {
            assertEquals(platform, BackendJson.decodeFromString(DevicePlatform.serializer(), "\"${platform.name}\""))
        }
        for (text in listOf("\"ios\"", "\"WEB\"", "\"UNKNOWN\"", "null", "0", "{}")) {
            assertFailsWith<SerializationException>(text) {
                BackendJson.decodeFromString(DevicePlatform.serializer(), text)
            }
        }
    }

    /** The confirmation text, `{}` and an absent `data` all succeed with one request each. */
    private suspend fun assertAnswersNeedNoData(
        confirmation: String,
        call: suspend BackendClient.() -> Unit,
    ) {
        for (body in listOf(envelope(confirmation), envelope("{}"), """{"success":true}""")) {
            val backend = MockBackend { ok(body) }

            backend.client.call()

            assertEquals(1, backend.requests.size, body)
        }
    }
}
