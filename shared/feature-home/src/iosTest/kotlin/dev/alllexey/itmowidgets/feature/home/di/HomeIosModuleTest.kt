package dev.alllexey.itmowidgets.feature.home.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.home.HomeCardRenderer
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.feature.home.data.HintHomeCardSource
import dev.alllexey.itmowidgets.feature.home.data.IosHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.data.PlacedWidgetKinds
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import dev.alllexey.itmowidgets.feature.home.presentation.HomeViewModel
import dev.alllexey.itmowidgets.feature.home.ui.HintHomeCardRenderer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The home route's graph as the iOS app starts it (`IosKoinModules`): the core module, [homeModule] and
 * [homeIosModule], with the account types, the services opt-in (`settingsDataModule` on iOS) and the simulator test
 * binary's device stood in. The feed's other sources come with their features' modules and their own tests.
 */
class HomeIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-home-test-${Random.nextLong().toULong()}"
    private val requests = mutableListOf<String>()

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheHomeRouteResolvesWithoutARequest() {
        val widgets = PlacedWidgetKinds { completion -> completion(emptySet()) }
        val koin = graph(
            iosCoreModule(FakeHost(), ORIGIN), homeModule, homeIosModule(widgets), platformTypes(), testDevice()
        )
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition -> koin.get<Any>(definition.primaryType, definition.qualifier) }

        koin.get<HomeViewModel>()
        assertIs<IosHomeHintStatus>(koin.get<HomeHintStatus>())
        assertSame<HomeCardSource>(koin.get<HintHomeCardSource>(), koin.get<HomeCardSource>(hintCardsQualifier))
        assertEquals(listOf<HomeCardRenderer>(HintHomeCardRenderer), koin.getAll<HomeCardRenderer>())
        assertEquals(emptyList(), requests)
        koin.close()
    }

    private fun platformTypes() = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<SessionRepository> { FakeSessionRepository(SessionState.SignedOut) }
        single<CustomServicesRepository> { FakeCustomServicesRepository(enabled = true) }
    }

    /** A counting engine, directories and the App Group under the test's temporary directory, no main queue. */
    private fun testDevice() = module {
        single<HttpClientEngine> {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<AppDirectories> {
            object : AppDirectories {
                override val files = root / "files"
                override val cache = root / "caches"
                override val noBackup = root / "no-backup"
            }
        }
        single<AppGroupDirectory> {
            AppGroupDirectory.resolve(
                identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
                appDirectories = get(),
                log = get<AppLog>(),
                containerOf = { root / "group" },
            )
        }
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
