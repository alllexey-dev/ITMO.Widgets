package dev.alllexey.itmowidgets.core.demo

import android.content.Context
import android.content.ContextWrapper
import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.connections.PairLink
import dev.alllexey.itmowidgets.core.network.asBackendTokenSource
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
import dev.alllexey.itmowidgets.feature.connections.data.DevicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.connections.data.PairingRepositoryImpl
import dev.alllexey.itmowidgets.feature.connections.domain.PairingError
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import io.ktor.client.engine.mock.MockEngine
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Single-call clients of the demo session: the pass, the update offer, «Устройства», pairing, privacy and Backend
 * sessions.
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
    private val unreachableSession = MyItmoClient(MyItmoConfiguration.DEFAULT, unreachableTokens, unreachableEngine, Clock.System)
    private val backendClient = BackendClient("https://backend.test", unreachableSession.tokens.asBackendTokenSource(), unreachableEngine)
    private val pairLink = PairLink.parse(
        "https://widgets.alllexey.dev/pair#1.ABCD2345.QxDul9iMwfCIpVdsd6sM9cOseX89lROcbIS1QpxZZio.AAECAwQFBgcICQoLDA0ODw"
    )!!

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
    fun `no update is offered and the devices and pairing are refused`() = runTest {
        val update = update(gate, demo)
        val devices = DevicesRepositoryImpl(gate, backendClient.connections, demo, dispatchers)
        val pairing = PairingRepositoryImpl(gate, backendClient.connections, demo, dispatchers)

        assertNull(update.loadUpdate())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), devices.refresh())
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), devices.revoke(Uuid.random()))
        assertEquals(PairingResult.Failure(PairingError.Failed(AppError.DemoUnavailable)), pairing.open(pairLink))
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
        val devices = DevicesRepositoryImpl(optedOut, backendClient.connections, noDemo, dispatchers)
        val pairing = PairingRepositoryImpl(optedOut, backendClient.connections, noDemo, dispatchers)
        val privacy = settingsRepository(stored, optedOut, noDemo)

        assertNull(update(optedOut, noDemo).loadUpdate())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), devices.refresh())
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), devices.revoke(Uuid.random()))
        assertEquals(PairingResult.Failure(PairingError.Failed(AppError.CustomServicesDisabled)), pairing.open(pairLink))
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
        val devices = DefaultBackendDeviceSession(
            gate, utility, backendClient.device, "Pixel", user, demo, dispatchers,
            ClientVersion("2.3.0", "20300", "android", "github")
        )
        val identity = DefaultBackendIdentitySync(
            gate, unreachableSession.tokens, unreachableTokens, backendClient.users, RecordingDiagnostics(), demo,
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
        stores.deviceHints,
        stores.appearance,
        stores.pushCategories,
        gate,
        backendClient.users,
        demo,
        dispatchers
    )

    /** MyItmoApi 2.x whose every request, token read included, fails the test. */
    private fun unreachableMyItmoClient(): MyItmoClient = MyItmoClient(
        MyItmoConfiguration.DEFAULT,
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
