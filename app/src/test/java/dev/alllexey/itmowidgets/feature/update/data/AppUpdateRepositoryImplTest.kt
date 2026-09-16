package dev.alllexey.itmowidgets.feature.update.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.AppVersionInfo
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics

class AppUpdateRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-09-15T10:00:00Z")

    @Test
    fun `reports a newer release with its note`() = runTest {
        val fixture = createRepository(versionInfo = versionInfo(latest = "2.2", note = " Новые виджеты "))

        val update = fixture.repository.loadUpdate()

        assertEquals(AppVersionName("2.1"), update?.installed)
        assertEquals(AppVersionName("2.2"), update?.latest)
        assertEquals("Новые виджеты", update?.note)
        assertEquals(false, update?.unsupported)
    }

    @Test
    fun `stays silent when the installed build is current or ahead`() = runTest {
        assertNull(createRepository(versionInfo = versionInfo(latest = "2.1")).repository.loadUpdate())
        assertNull(createRepository(versionInfo = versionInfo(latest = "2.0.9")).repository.loadUpdate())
    }

    @Test
    fun `marks a build the backend no longer supports`() = runTest {
        val fixture = createRepository(versionInfo = versionInfo(latest = "2.2", min = "2.2"))

        assertEquals(true, fixture.repository.loadUpdate()?.unsupported)
    }

    @Test
    fun `never reaches the backend without the opt-in`() = runTest {
        val fixture = createRepository(customServicesEnabled = false)

        assertNull(fixture.repository.loadUpdate())
        assertEquals(0, fixture.api.versionInfoCalls)
    }

    @Test
    fun `a failed check offers nothing instead of failing the caller`() = runTest {
        val fixture = createRepository()
        fixture.api.versionInfoResponse = { throw IOException("offline") }

        assertNull(fixture.repository.loadUpdate())
    }

    @Test
    fun `remembers when the offer was shown and which release was skipped`() = runTest {
        val fixture = createRepository()

        assertEquals(Instant.EPOCH, fixture.repository.reminder().notifiedAt)
        // Nothing is skipped yet: the installed build is its own floor.
        assertEquals(AppVersionName("2.1"), fixture.repository.reminder().skippedVersion)

        fixture.repository.markNotified()
        fixture.repository.skip(AppVersionName("2.2"))

        assertEquals(now, fixture.repository.reminder().notifiedAt)
        assertEquals(AppVersionName("2.2"), fixture.repository.reminder().skippedVersion)
    }

    private fun createRepository(
        versionInfo: AppVersionInfo = versionInfo(),
        customServicesEnabled: Boolean = true
    ): Fixture {
        val api = FakeItmoWidgetsApi().apply { versionInfoResponse = { ApiResponse.success(versionInfo) } }
        val storage = UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = INSTALLED_VERSION)
        return Fixture(
            api = api,
            repository = AppUpdateRepositoryImpl(
                widgetsApi = api.instance,
                customServices = FakeCustomServicesRepository(customServicesEnabled),
                utilityStorage = storage,
                installedVersion = AppVersionName(INSTALLED_VERSION),
                clock = Clock.fixed(now, ZoneOffset.UTC),
                diagnostics = RecordingDiagnostics()
            )
        )
    }

    private fun versionInfo(
        latest: String = "2.2",
        min: String = "1.0",
        note: String = ""
    ) = AppVersionInfo(minVersion = min, latestVersion = latest, note = note)

    private data class Fixture(
        val api: FakeItmoWidgetsApi,
        val repository: AppUpdateRepositoryImpl
    )

    private class FakeCustomServicesRepository(private val enabled: Boolean) : CustomServicesRepository {
        override fun observeEnabled(): Flow<Boolean> = MutableStateFlow(enabled).asStateFlow()

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun setEnabled(enabled: Boolean) = Unit
    }

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

    private class FakeItmoWidgetsApi {
        var versionInfoCalls: Int = 0
            private set
        var versionInfoResponse: () -> ApiResponse<AppVersionInfo> = {
            ApiResponse.success(AppVersionInfo("1.0", "2.2", ""))
        }

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java)
        ) { proxy, method, arguments ->
            when (method.name) {
                "appVersionInfo" -> {
                    versionInfoCalls += 1
                    versionInfoResponse()
                }
                "equals" -> proxy === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "FakeItmoWidgetsApi"
                else -> error("Unexpected ItmoWidgetsApi call: ${method.name}")
            }
        } as ItmoWidgetsApi
    }

    private companion object {
        const val INSTALLED_VERSION = "2.1"
    }
}
