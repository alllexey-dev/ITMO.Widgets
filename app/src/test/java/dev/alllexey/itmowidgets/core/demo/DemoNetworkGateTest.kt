package dev.alllexey.itmowidgets.core.demo

import android.content.Context
import android.content.ContextWrapper
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.network.BackendClientFactory
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.DefaultBackendDeviceSession
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.ui.rendering.QrCodeGenerator
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.update.data.AppUpdateRepositoryImpl
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Single-call clients of the demo session: the pass, the update offer, web sign-in, privacy and Backend sessions.
 * The Backend ones send nothing in the demo, even with the stored opt-in, and nothing without the opt-in; the Core 2.0
 * identity sync does not even read the ITMO.ID session.
 */
class DemoNetworkGateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    private val demo = FakeDemoMode(active = true)
    private val stores = PreferenceStores().also {
        kotlinx.coroutines.runBlocking { it.servicesOptIn.setCustomServicesEnabled(true) }
    }
    private val gate = DefaultBackendGate(stores.servicesOptIn, demo)

    /** The 2.x session and Core 2.0 over storage and an engine that fail the test on any use. */
    private val unreachableTokens = object : TokenStorage {
        override suspend fun read(): TokenSet? = throw AssertionError("The demo session read the ITMO.ID session")

        override suspend fun write(tokens: TokenSet?) = throw AssertionError("The demo session wrote the ITMO.ID session")
    }
    private val unreachableEngine = MockEngine { request -> throw AssertionError("The demo session asked ${request.url}") }
    private val unreachableSession = MyItmoClientFactory.create(unreachableTokens, unreachableEngine, Clock.System)
    private val backendClient = BackendClientFactory.create("https://backend.test", unreachableSession.tokens, unreachableEngine)

    @Test
    fun `the pass is a code no turnstile accepts`() = runTest {
        assertEquals(DemoQr.HEX, QrCodeRemoteDataSourceImpl(unreachableMyItmoClient(), demo, dispatchers).getQrHex())
        assertTrue(DemoQr.HEX.startsWith("DEMO"))
    }

    @Test
    fun `the demo pass fits the code the pass screen draws`() {
        assertEquals(1, QrCodeGenerator().generate(DemoQr.HEX).version)
    }

    @Test
    fun `no update is offered and web sign-in is refused`() = runTest {
        val update = update(gate, demo)
        val webLogin = WebLoginRepositoryImpl(gate, backendClient.users, demo, dispatchers)

        assertNull(update.loadUpdate())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), webLogin.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), webLogin.approve(Uuid.random()))
    }

    @Test
    fun `privacy shows the defaults and refuses changes`() = runTest {
        val repository = settingsRepository(stores, gate, demo)

        repository.refreshSharingSettings()

        assertTrue(repository.observeSharingSettings().first() is SharingSettingsState.Content)
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), repository.setScheduleVisibility(SharingVisibility.ALL))
    }

    @Test
    fun `Backend never learns about the demo session`() = runTest {
        assertBackendSessionsStayLocal(gate, demo)
    }

    @Test
    fun `without the opt-in no Backend client sends anything`() = runTest {
        val noDemo = FakeDemoMode()
        val stored = PreferenceStores()
        val optedOut = DefaultBackendGate(stored.servicesOptIn, noDemo)
        val webLogin = WebLoginRepositoryImpl(optedOut, backendClient.users, noDemo, dispatchers)
        val privacy = settingsRepository(stored, optedOut, noDemo)

        assertNull(update(optedOut, noDemo).loadUpdate())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), webLogin.preview("ABCD2345"))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), webLogin.approve(Uuid.random()))
        privacy.refreshSharingSettings()
        assertEquals(SharingSettingsState.Disabled, privacy.observeSharingSettings().first())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), privacy.setScheduleVisibility(SharingVisibility.ALL))
        assertBackendSessionsStayLocal(optedOut, noDemo)
        assertFalse(optedOut.mayCallBackend())
    }

    private fun update(gate: BackendGate, demo: DemoMode) = AppUpdateRepositoryImpl(
        backendClient.app, gate, UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = "2.2"), AppVersionName("2.2"),
        DevicePlatform.ANDROID, Clock.System, RecordingDiagnostics(), demo,
        dispatchers = dispatchers
    )

    private suspend fun assertBackendSessionsStayLocal(gate: BackendGate, demo: DemoMode) {
        val user = object : CurrentUserProvider {
            override suspend fun getCurrentUser() = CurrentUser(DemoPeople.ME_ISU, DemoPeople.ME_NAME, null)
        }
        val utility = UtilityStorage(InMemoryPreferencesDataStore(), appVersionName = "2.2").also {
            it.setFirebaseToken("demo-token")
        }
        val devices = DefaultBackendDeviceSession(gate, utility, backendClient.device, "Pixel", user, demo, dispatchers)
        val identity = DefaultBackendIdentitySync(
            unusedContext(), gate, unreachableSession.tokens, unreachableTokens, backendClient.users, RecordingDiagnostics(), demo,
            dispatchers
        )

        devices.registerCurrentDevice()
        devices.unregisterCurrentDevice()

        assertTrue(identity.sync())
        assertTrue(unreachableEngine.requestHistory.isEmpty())
    }

    private fun settingsRepository(stores: PreferenceStores, gate: BackendGate, demo: DemoMode) = SettingsRepositoryImpl(
        stores.servicesOptIn,
        stores.scheduleChecks,
        stores.widgetSettings,
        stores.qrSettings,
        stores.sportSignSelectors,
        stores.markSources,
        stores.homeLayout,
        stores.deviceHints,
        gate,
        backendClient.users,
        demo,
        dispatchers
    )

    /** MyItmoApi 2.x whose every request, token read included, fails the test. */
    private fun unreachableMyItmoClient(): MyItmoClient = MyItmoClientFactory.create(
        storage = object : TokenStorage {
            override suspend fun read(): TokenSet? = throw AssertionError("The demo session read the ITMO session")

            override suspend fun write(tokens: TokenSet?) = throw AssertionError("The demo session wrote the ITMO session")
        },
        engine = MockEngine { request -> throw AssertionError("The demo session asked ${request.url.host}${request.url.encodedPath}") },
        clock = Clock.System
    )

    /** A context nobody may touch: allocated without Android's stub constructor, any call on it fails. */
    private fun unusedContext(): Context {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, ContextWrapper::class.java) as Context
    }
}
