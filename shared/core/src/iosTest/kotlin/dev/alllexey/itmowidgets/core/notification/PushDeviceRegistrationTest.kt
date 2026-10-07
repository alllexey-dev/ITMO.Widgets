package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The iOS push registration against Core 2.0 on a MockEngine (IO-13a): what reaches Backend in each state of the
 * opt-in, the demo, the alerts answer, the push token and the account. Every token and ISU is synthetic.
 */
class PushDeviceRegistrationTest {

    private val requests = mutableListOf<String>()
    private var status = HttpStatusCode.OK
    private val engine = MockEngine { request ->
        val body = (request.body as? TextContent)?.text.orEmpty()
        requests += "${request.method.value} ${request.url.encodedPath} $body"
        // Backend's envelope around its confirmation text, which the client drops.
        respond(
            """{"success":true,"data":"synthetic"}""",
            status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    private val demo = FakeDemoMode()
    private val gate = FakeBackendGate(optedIn = true, demo = demo)
    private val device = IosPushDevice(appVersion = "2.3.0", readAlertsAllowed = { alerts })
    private val registrations = PushRegistrationPreferences(InMemoryPreferencesDataStore())
    private var user: CurrentUser? = CurrentUser(isu = ISU, name = null, pictureUrl = null)
    private var alerts = true

    private val registration = PushDeviceRegistration(
        devices = BackendClient(ORIGIN, AccessTokenSource { "synthetic-access" }, engine).device,
        device = device,
        registrations = registrations,
        gate = gate,
        demo = demo,
        currentUser = object : CurrentUserProvider {
            override suspend fun getCurrentUser(): CurrentUser? = user
        },
    )

    @Test
    fun registersTheIosPlatformTheAlertsAnswerAndTheAppVersionOnce() = runTest {
        device.updateToken(" synthetic-fcm ")

        registration.sync()
        registration.sync()

        assertEquals(listOf(register(alertsAllowed = true)), requests)
        assertEquals(PushRegistration("synthetic-fcm", ISU, alertsAllowed = true), registrations.get())
    }

    @Test
    fun aChangedAlertsAnswerRegistersAgain() = runTest {
        device.updateToken("synthetic-fcm")
        registration.sync()

        alerts = false
        registration.sync()

        assertEquals(listOf(register(alertsAllowed = true), register(alertsAllowed = false)), requests)
        assertEquals(false, registrations.get()?.alertsAllowed)
        assertEquals(false, device.observeAlertsAllowed().value)
    }

    @Test
    fun alertsOffAreRegisteredAsOff() = runTest {
        alerts = false
        device.updateToken("synthetic-fcm")

        registration.registerCurrentDevice()

        assertEquals(listOf(register(alertsAllowed = false)), requests)
    }

    @Test
    fun withoutTheOptInNothingIsSent() = runTest {
        gate.optedIn.value = false
        device.updateToken("synthetic-fcm")

        registration.sync()
        registration.registerCurrentDevice()
        registration.unregisterCurrentDevice()

        assertEquals(emptyList(), requests)
        assertNull(registrations.get())
    }

    @Test
    fun theDemoSendsNothingEvenWithTheOptIn() = runTest {
        demo.active.value = true
        device.updateToken("synthetic-fcm")

        registration.sync()
        registration.registerCurrentDevice()
        registration.unregisterCurrentDevice()

        assertEquals(emptyList(), requests)
    }

    @Test
    fun noTokenOrABlankOneIsNeverRegistered() = runTest {
        registration.sync()
        registration.registerCurrentDevice()

        device.updateToken("   ")
        registration.sync()
        registration.unregisterCurrentDevice()

        assertEquals(emptyList(), requests)
        assertNull(registrations.get())
    }

    @Test
    fun noSignedInIsuRegistersNothing() = runTest {
        user = null
        device.updateToken("synthetic-fcm")

        registration.sync()

        assertEquals(emptyList(), requests)
    }

    @Test
    fun anotherAccountRegistersTheSameTokenAgain() = runTest {
        device.updateToken("synthetic-fcm")
        registration.sync()

        user = CurrentUser(isu = OTHER_ISU, name = null, pictureUrl = null)
        registration.sync()

        assertEquals(2, requests.size)
        assertEquals(OTHER_ISU, registrations.get()?.ownerIsu)
    }

    @Test
    fun unregisterDetachesTheRegisteredTokenAndForgetsIt() = runTest {
        device.updateToken("synthetic-fcm")
        registration.sync()
        device.updateToken("synthetic-fcm-rotated")

        registration.unregisterCurrentDevice()

        assertEquals(
            listOf(register(alertsAllowed = true), """DELETE /api/device/current {"fcmToken":"synthetic-fcm"}"""),
            requests
        )
        assertNull(registrations.get())

        // Signed in again: the stored registration is gone, so the next sync registers the current token.
        registration.sync()
        assertEquals(3, requests.size)
    }

    @Test
    fun theForegroundRefreshPublishesTheAlertsAnswerWithoutTheOptIn() = runTest {
        gate.optedIn.value = false
        val log = RecordingAppLog()

        PushForegroundRefresh(device, registration, log).refresh()

        assertEquals(true, device.observeAlertsAllowed().value)
        assertEquals(emptyList(), requests)
        assertEquals(emptyList(), log.lines)
    }

    @Test
    fun theForegroundRefreshLogsABackendFailureInsteadOfThrowingIntoSwift() = runTest {
        status = HttpStatusCode.ServiceUnavailable
        device.updateToken("synthetic-fcm")
        val log = RecordingAppLog()

        PushForegroundRefresh(device, registration, log).refresh()

        assertEquals(listOf("WARN:PushRegistration:Push registration sync failed"), log.lines)
        assertNull(registrations.get(), "a refused registration is not recorded, so the next refresh retries")
    }

    private fun register(alertsAllowed: Boolean) =
        "POST /api/device/register-device " +
            """{"fcmToken":"synthetic-fcm","deviceName":"${device.name}","platform":"IOS",""" +
            """"alertsAllowed":$alertsAllowed,"appVersion":"2.3.0"}"""

    private companion object {
        const val ORIGIN = "https://backend.test"
        const val ISU = 100001
        const val OTHER_ISU = 100002
    }
}
