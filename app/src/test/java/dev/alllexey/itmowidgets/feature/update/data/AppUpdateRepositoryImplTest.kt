package dev.alllexey.itmowidgets.feature.update.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

/** The update offer over Core 2.0 and MockEngine. */
class AppUpdateRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val now: Instant = Instant.parse("2026-09-15T10:00:00Z")

    @Test
    fun `reports a newer release with its note`() = runTest {
        val fixture = createRepository(versionInfo(latest = "2.2", note = " Новые виджеты "))

        val update = fixture.repository.loadUpdate()

        assertEquals(AppVersionName("2.1"), update?.installed)
        assertEquals(AppVersionName("2.2"), update?.latest)
        assertEquals("Новые виджеты", update?.note)
        assertEquals(false, update?.unsupported)
    }

    @Test
    fun `asks Backend for the Android versions`() = runTest {
        val fixture = createRepository(answer = { respondJson(contractFixture("http/app/appVersionInfo.json")) })

        assertEquals(AppVersionName("2.2"), fixture.repository.loadUpdate()?.latest)

        val request = fixture.harness.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/app/version-info", request.url.encodedPath)
        assertEquals("ANDROID", request.url.parameters["platform"])
    }

    @Test
    fun `stays silent when the installed build is current or ahead`() = runTest {
        assertNull(createRepository(versionInfo(latest = "2.1")).repository.loadUpdate())
        assertNull(createRepository(versionInfo(latest = "2.0.9")).repository.loadUpdate())
    }

    @Test
    fun `marks a build the backend no longer supports`() = runTest {
        val fixture = createRepository(versionInfo(latest = "2.2", min = "2.2"))

        assertEquals(true, fixture.repository.loadUpdate()?.unsupported)
    }

    @Test
    fun `never reaches the backend without the opt-in`() = runTest {
        val fixture = createRepository(customServicesEnabled = false)

        assertNull(fixture.repository.loadUpdate())
        assertTrue(fixture.harness.requests.isEmpty())
    }

    @Test
    fun `the demo session offers nothing and sends nothing, even with the stored opt-in`() = runTest {
        val fixture = createRepository(demo = FakeDemoMode(active = true))

        assertNull(fixture.repository.loadUpdate())
        assertTrue(fixture.harness.requests.isEmpty())
    }

    @Test
    fun `a failed check offers nothing instead of failing the caller`() = runTest {
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
            assertEquals(1, fixture.diagnostics.messages.size)
        }
    }

    @Test
    fun `remembers when the offer was shown and which release was skipped`() = runTest {
        val fixture = createRepository()

        assertEquals(Instant.fromEpochMilliseconds(0), fixture.repository.reminder().notifiedAt)
        // Nothing is skipped yet: the installed build is its own floor.
        assertEquals(AppVersionName("2.1"), fixture.repository.reminder().skippedVersion)

        fixture.repository.markNotified()
        fixture.repository.skip(AppVersionName("2.2"))

        assertEquals(now, fixture.repository.reminder().notifiedAt)
        assertEquals(AppVersionName("2.2"), fixture.repository.reminder().skippedVersion)
    }

    private fun createRepository(
        versionInfo: String = versionInfo(),
        customServicesEnabled: Boolean = true,
        demo: FakeDemoMode = FakeDemoMode(),
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson(versionInfo) }
    ): Fixture {
        val harness = Core2Harness(session(), backend = answer)
        val diagnostics = RecordingDiagnostics()
        val storage = UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = INSTALLED_VERSION)
        return Fixture(
            harness = harness,
            diagnostics = diagnostics,
            repository = AppUpdateRepositoryImpl(
                app = harness.client.app,
                backend = FakeBackendGate(customServicesEnabled, demo),
                utilityStorage = storage,
                installedVersion = AppVersionName(INSTALLED_VERSION),
                clock = object : Clock {
                    override fun now(): Instant = now
                },
                diagnostics = diagnostics,
                demo = demo,
                dispatchers = dispatchers
            )
        )
    }

    private fun versionInfo(
        latest: String = "2.2",
        min: String = "1.0",
        note: String = ""
    ) = """{"success":true,"data":{"minVersion":"$min","latestVersion":"$latest","note":"$note"},"error":null}"""

    private class Fixture(
        val harness: Core2Harness,
        val diagnostics: RecordingDiagnostics,
        val repository: AppUpdateRepositoryImpl
    )

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        private val mutex = Mutex()

        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }

    private companion object {
        const val INSTALLED_VERSION = "2.1"
    }
}
