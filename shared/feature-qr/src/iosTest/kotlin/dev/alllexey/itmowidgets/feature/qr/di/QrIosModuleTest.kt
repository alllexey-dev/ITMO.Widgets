package dev.alllexey.itmowidgets.feature.qr.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import dev.alllexey.itmowidgets.feature.qr.widget.QrPassSnapshotWriter
import dev.alllexey.itmowidgets.feature.qr.widget.TestDevice
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The QR route's graph as the iOS app starts it (`IosKoinModules`): the core module, [qrModule] and [qrIosModule],
 * with the account types (KM-11h1) and the simulator test binary's device stood in.
 */
class QrIosModuleTest {

    private val device = TestDevice()
    private val requests = mutableListOf<String>()

    @AfterTest
    fun deleteDevice() = device.delete()

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheQrRouteResolvesAndTheSnapshotWriterStartsWithTheGraph() {
        val koin = graph(iosCoreModule(FakeHost(), ORIGIN), qrModule, qrIosModule, accountTypes(), testDevice())
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition -> koin.get<Any>(definition.primaryType, definition.qualifier) }

        koin.get<QrCodeViewModel>()
        assertSame<Any>(koin.get<QrCodeRepository>(), koin.get<SessionDataCleaner>(named("qr")))
        koin.get<QrPassSnapshotWriter>()
        assertEquals(emptyList(), requests)
        koin.close()
    }

    private fun accountTypes() = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<SessionRepository> { FakeSessionRepository(SessionState.SignedOut) }
    }

    /**
     * A counting engine, directories and the App Group under the test's temporary directory, and no main queue (the
     * test blocks it).
     */
    private fun testDevice() = module {
        single<HttpClientEngine> {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<AppDirectories> { device.directories }
        single<AppGroupDirectory> { device.appGroup(get<AppLog>()) }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
    }

    private fun graph(vararg modules: Module): Koin = koinApplication {
        allowOverride(true)
        modules(*modules)
    }.koin

    private class FakeHost : IosCoreHost {
        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
    }
}
