package dev.alllexey.itmowidgets.core.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/** The push registration of this installation over Core 2.0 and MockEngine. */
class BackendDeviceRegistrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test fun `only successful registration is persisted with its owner and unregister clears it`() = runTest {
        val fixture = Fixture()
        fixture.prepare()
        fixture.device.registerCurrentDevice()
        assertEquals("synthetic-token", fixture.utility.getRegisteredFirebaseToken())
        assertEquals(123456, fixture.utility.getRegisteredFirebaseOwner())
        assertEquals(fixture.version.headerValue, fixture.utility.getRegisteredFirebaseAppVersion())
        fixture.device.unregisterCurrentDevice()
        assertNull(fixture.utility.getRegisteredFirebaseToken())
        assertNull(fixture.utility.getRegisteredFirebaseOwner())
        assertNull(fixture.utility.getRegisteredFirebaseAppVersion())
        assertEquals(
            listOf("POST /api/device/register-device", "DELETE /api/device/current"),
            fixture.harness.requests.map { "${it.method.value} ${it.url.encodedPath}" }
        )
        assertTrue(fixture.harness.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer stored-access" })
    }

    @Test fun `registration names Android and leaves alerts and the app version to Backend's defaults`() = runTest {
        val fixture = Fixture()
        fixture.prepare()

        fixture.device.registerCurrentDevice()

        val body = Json.parseToJsonElement(fixture.harness.requests.single().bodyText()).jsonObject
        assertEquals(
            Json.parseToJsonElement("""{"fcmToken":"synthetic-token","deviceName":"Synthetic device","platform":"ANDROID"}"""),
            body
        )
        assertFalse("alertsAllowed" in body)
        assertFalse("appVersion" in body)
    }

    @Test fun `unregistration sends the token in the body, never in the URL`() = runTest {
        val fixture = Fixture()
        fixture.prepare()

        fixture.device.unregisterCurrentDevice()

        val request = fixture.harness.requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertFalse("synthetic-token" in request.url.toString())
        assertEquals(Json.parseToJsonElement("""{"fcmToken":"synthetic-token"}"""), Json.parseToJsonElement(request.bodyText()))
    }

    @Test fun `backend rejection never records a successful token sync`() = runTest {
        val rejections = listOf<suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>(
            { respondJson(errorEnvelope("unauthorized"), HttpStatusCode.Unauthorized) },
            { respondJson(errorEnvelope("restricted"), HttpStatusCode.Forbidden) },
            { respondJson("""{"success":false,"data":null,"error":{"message":"synthetic","code":"rejected"}}""") }
        )
        for (rejection in rejections) {
            val fixture = Fixture(rejection)
            fixture.prepare()
            try {
                fixture.device.registerCurrentDevice()
                fail("Expected rejection")
            } catch (_: BackendException) {
            }
            assertNull(fixture.utility.getRegisteredFirebaseToken())
            assertNull(fixture.utility.getRegisteredFirebaseOwner())
        }
    }

    @Test fun `disabled services signed out and missing token never call backend`() = runTest {
        val fixture = Fixture()
        fixture.device.registerCurrentDevice()
        fixture.prepare()
        fixture.owner = null
        fixture.device.registerCurrentDevice()
        fixture.owner = 123456
        fixture.utility.setFirebaseToken(null)
        fixture.device.registerCurrentDevice()
        fixture.device.unregisterCurrentDevice()
        assertTrue(fixture.harness.requests.isEmpty())
    }

    @Test fun `the demo session sends nothing, even with the stored opt-in`() = runTest {
        val fixture = Fixture(demo = FakeDemoMode(active = true))
        fixture.prepare()

        fixture.device.registerCurrentDevice()
        fixture.device.unregisterCurrentDevice()

        assertTrue(fixture.harness.requests.isEmpty())
        assertNull(fixture.utility.getRegisteredFirebaseToken())
    }

    private inner class Fixture(
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
            val path = if (request.method == HttpMethod.Delete) "unregisterCurrentDevice" else "registerDevice"
            respondJson(contractFixture("http/device/$path.json"))
        },
        demo: FakeDemoMode = FakeDemoMode(),
    ) {
        val settings = ServicesOptInPreferences(MemoryPreferences())
        val utility = UtilityStorage(MemoryPreferences(), "test")
        var owner: Int? = 123456
        val version = ClientVersion("2.3.0", "20300", "android", "github")
        val harness = Core2Harness(session(), backend = answer)
        val device = DefaultBackendDeviceSession(
            DefaultBackendGate(settings, demo), utility, harness.client.device, "Synthetic device",
            object : CurrentUserProvider {
                override suspend fun getCurrentUser() = owner?.let { CurrentUser(it, "Synthetic user", null) }
            },
            demo, mainDispatcherRule.appDispatchers, version
        )

        suspend fun prepare() {
            settings.setCustomServicesEnabled(true)
            utility.setFirebaseToken("synthetic-token")
        }
    }

    private class MemoryPreferences : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
