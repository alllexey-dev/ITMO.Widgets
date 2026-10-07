package dev.alllexey.itmowidgets.core.di

import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.diagnostics.IosAppDiagnostics
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.platform.IosPlatformCapabilities
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.KeychainSessionDataCleaner
import dev.alllexey.itmowidgets.core.session.KeychainTokenStorage
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionSnapshot
import dev.alllexey.itmowidgets.core.session.SessionSnapshotWriter
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.IosAppDirectories
import dev.alllexey.itmowidgets.core.storage.KeychainSecureStore
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.TemporaryDirectory
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

class IosCoreModuleTest {

    private val temporary = TemporaryDirectory()
    private val host = FakeHost()
    private val demo = FakeDemoMode()
    private val requests = mutableListOf<String>()

    /** What the account module (KM-11h1) adds to the graph: the only types the core module does not define. */
    private val accountTypes = module {
        single<DemoMode> { demo }
        single<SessionRepository> { FakeSessionRepository(SessionState.SignedOut) }
    }

    @AfterTest
    fun deleteTemporary() = temporary.delete()

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionResolvesWithTheAccountTypesGiven() {
        val koin = graph(iosCoreModule(host, ORIGIN), accountTypes, appGroupStandIn())
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier)
        }

        assertTrue(definitions.size >= MIN_DEFINITIONS, "only ${definitions.size} definitions")
        assertEquals(3, koin.getAll<SessionDataCleaner>().size)
        val storage = koin.get<KeychainTokenStorage>()
        assertSame(storage, koin.get<TokenStorage>())
        assertSame(storage, koin.get<SessionTokenStore>())
        assertSame(koin.get<KeychainSecureStore>(), koin.get<SecureStore>())
        assertSame(koin.get<IosAppDiagnostics>(), koin.get<AppDiagnostics>())
        assertEquals(IosPlatformCapabilities, koin.get<PlatformCapabilities>())
        koin.close()
    }

    @Test
    fun theDemoSessionSendsNothingWhileItsGraphIsBuiltSignedOutAndSnapshotted() = runTest {
        val koin = graph(iosCoreModule(host, ORIGIN), accountTypes, appGroupStandIn(), testDevice())
        demo.active.value = true

        withContext(Dispatchers.Default) {
            koin.get<ServicesOptInPreferences>().setCustomServicesEnabled(true)
            val gate = koin.get<BackendGate>()
            assertFalse(gate.mayCallBackend())
            assertTrue(gate.isConnected())
            assertTrue(gate.isOptedIn())

            koin.get<MyItmoClient>()
            koin.get<BackendClient>()
            koin.get<SessionSnapshotWriter>().write(SessionSnapshot(isu = null, demo = true, alertsAllowed = false))
            koin.getAll<SessionDataCleaner>()
                .filterNot { it is KeychainSessionDataCleaner }
                .forEach { it.clearSessionData() }
        }

        assertEquals(emptyList(), requests)
        assertEquals(1, host.websiteDataClears)
        demo.active.value = false
        withContext(Dispatchers.Default) { assertTrue(koin.get<BackendGate>().mayCallBackend()) }
        koin.close()
    }

    /**
     * The group container under the test's temporary directory: the test binary has no App Group, and
     * `AppGroupDirectoryTest` owns the once-per-process fallback warning.
     */
    private fun appGroupStandIn() = module {
        single { AppGroupDirectory(temporary.root / "group", isShared = true) }
    }

    /**
     * The simulator test binary's other stand-ins: a counting engine, directories under the test's own temporary
     * directory, and no main queue (the test blocks it). The Keychain has no entitlement here; the hosted
     * `SessionTests` cover it.
     */
    private fun testDevice() = module {
        single<HttpClientEngine> {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<AppDirectories> {
            IosAppDirectories.create(temporary.root / "support", temporary.root / "caches", get<AppLog>())
        }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
    }

    private fun graph(vararg modules: Module): Koin = koinApplication {
        allowOverride(true)
        modules(*modules)
    }.koin

    private class FakeHost : IosCoreHost {
        var websiteDataClears = 0

        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) {
            websiteDataClears++
            completion()
        }

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"

        /** The bindings listed in the module's KDoc; a lost one fails here before it fails at a call site. */
        const val MIN_DEFINITIONS = 27
    }
}
