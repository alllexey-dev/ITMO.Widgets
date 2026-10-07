package dev.alllexey.itmowidgets.feature.update.data

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

/** The update offer over the real Core 2.0 client and a MockEngine. */
class AppUpdateRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-09-15T10:00:00Z")

    @Test
    fun reportsANewerReleaseWithItsNote() = runTest {
        val fixture = createRepository(versionInfo(latest = "2.2", note = " Новые виджеты "))

        val update = fixture.repository.loadUpdate()

        assertEquals(AppVersionName("2.1"), update?.installed)
        assertEquals(AppVersionName("2.2"), update?.latest)
        assertEquals("Новые виджеты", update?.note)
        assertEquals(false, update?.unsupported)
    }

    @Test
    fun asksBackendForTheVersionsOfTheSuppliedPlatform() = runTest {
        for (platform in listOf(DevicePlatform.ANDROID, DevicePlatform.IOS)) {
            val fixture = createRepository(platform = platform, answer = { respondJson(VERSION_INFO_FIXTURE) })

            assertEquals(AppVersionName("2.2"), fixture.repository.loadUpdate()?.latest)

            val request = fixture.harness.requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/app/version-info", request.url.encodedPath)
            assertEquals(platform.name, request.url.parameters["platform"])
        }
    }

    @Test
    fun staysSilentWhenTheInstalledBuildIsCurrentOrAhead() = runTest {
        assertNull(createRepository(versionInfo(latest = "2.1")).repository.loadUpdate())
        assertNull(createRepository(versionInfo(latest = "2.0.9")).repository.loadUpdate())
    }

    @Test
    fun marksABuildTheBackendNoLongerSupports() = runTest {
        val fixture = createRepository(versionInfo(latest = "2.2", min = "2.2"))

        assertEquals(true, fixture.repository.loadUpdate()?.unsupported)
    }

    @Test
    fun neverReachesTheBackendWithoutTheOptIn() = runTest {
        val fixture = createRepository(customServicesEnabled = false)

        assertNull(fixture.repository.loadUpdate())
        assertTrue(fixture.harness.requests.isEmpty())
    }

    @Test
    fun theDemoSessionOffersNothingAndSendsNothingEvenWithTheStoredOptIn() = runTest {
        val fixture = createRepository(demo = FakeDemoMode(active = true))

        assertNull(fixture.repository.loadUpdate())
        assertTrue(fixture.harness.requests.isEmpty())
    }

    @Test
    fun aFailedCheckOffersNothingAndLeavesOneWarning() = runTest {
        val failures = listOf<suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>(
            { throw IOException("offline") },
            { respondJson(errorEnvelope("unauthorized"), HttpStatusCode.Unauthorized) },
            { respondJson(errorEnvelope("restricted"), HttpStatusCode.Forbidden) },
            { respondJson("""{"success":true,"data":{"minVersion":"2.1","latestVersion":null,"note":""},"error":null}""") }
        )
        for (failure in failures) {
            val fixture = createRepository(answer = failure)

            assertNull(fixture.repository.loadUpdate())
            assertEquals(1, fixture.harness.requests.size)
            assertEquals(listOf("WARNING:AppUpdate:Failed to read the latest app version"), fixture.diagnostics.messages)
        }
    }

    @Test
    fun remembersWhenTheOfferWasShownAndWhichReleaseWasSkipped() = runTest {
        val fixture = createRepository()

        assertEquals(Instant.fromEpochMilliseconds(0), fixture.repository.reminder().notifiedAt)
        // Nothing is skipped yet: the installed build is its own floor.
        assertEquals(AppVersionName("2.1"), fixture.repository.reminder().skippedVersion)

        fixture.repository.markNotified()
        fixture.repository.skip(AppVersionName("2.2"))

        assertEquals(now, fixture.repository.reminder().notifiedAt)
        assertEquals(AppVersionName("2.2"), fixture.repository.reminder().skippedVersion)
    }

    private fun TestScope.createRepository(
        versionInfo: String = versionInfo(),
        customServicesEnabled: Boolean = true,
        demo: FakeDemoMode = FakeDemoMode(),
        platform: DevicePlatform = DevicePlatform.ANDROID,
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson(versionInfo) }
    ): Fixture {
        val harness = BackendHarness(answer)
        val diagnostics = RecordingDiagnostics()
        val dispatcher = StandardTestDispatcher(testScheduler)
        return Fixture(
            harness = harness,
            diagnostics = diagnostics,
            repository = AppUpdateRepositoryImpl(
                app = harness.client.app,
                backend = FakeBackendGate(customServicesEnabled, demo),
                utilityStorage = UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = INSTALLED_VERSION),
                installedVersion = AppVersionName(INSTALLED_VERSION),
                platform = platform,
                clock = FakeClock(now),
                diagnostics = diagnostics,
                demo = demo,
                dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
            )
        )
    }

    private fun versionInfo(
        latest: String = "2.2",
        min: String = "1.0",
        note: String = ""
    ) = """{"success":true,"data":{"minVersion":"$min","latestVersion":"$latest","note":"$note"},"error":null}"""

    private class Fixture(
        val harness: BackendHarness,
        val diagnostics: RecordingDiagnostics,
        val repository: AppUpdateRepositoryImpl
    )

    /** The Core 2.0 client over one MockEngine with the stored access token; [requests] records every request. */
    private class BackendHarness(backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val client = BackendClient(
            "https://backend.test",
            AccessTokenSource { "stored-access" },
            MockEngine { request -> requests += request; backend(request) },
        )
    }

    private companion object {
        const val INSTALLED_VERSION = "2.1"

        /** Backend's contract fixture `http/app/appVersionInfo.json` of `:shared:backend-client`. */
        const val VERSION_INFO_FIXTURE = """{"success":true,"data":{"minVersion":"2.1","latestVersion":"2.2",""" +
            """"note":"Синтетическая заметка о версии"},"error":null}"""

        /** Backend's error envelope (`GlobalExceptionHandler`) with a synthetic message. */
        fun errorEnvelope(code: String): String =
            """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""
    }
}
