package dev.alllexey.itmowidgets.feature.resources.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import dev.alllexey.itmowidgets.feature.resources.presentation.LinkEditorViewModel
import dev.alllexey.itmowidgets.feature.resources.presentation.SubjectLinksViewModel
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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The subject links' graph as the iOS app starts it (`IosKoinModules`): the core module, [resourcesModule] and
 * [resourcesIosModule], with the account types, the services opt-in (`settingsDataModule` on iOS) and the simulator
 * test binary's device stood in, on the demo session: the sheets resolve with the arguments the Swift hosts give them
 * and answer from `DemoSubjectLinks` with no request; every change is refused in the demo without one.
 */
class ResourcesIosModuleTest {

    private val root: Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-resources-test-${Random.nextLong().toULong()}"
    private val requests = mutableListOf<String>()

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionOfTheLinkSheetsResolvesWithoutARequest() {
        val koin = graph()
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf(arguments()) }
        }

        koin.get<SubjectLinksViewModel> { parametersOf(arguments()) }
        koin.get<LinkEditorViewModel> { parametersOf(arguments(linkId = OWN_LINK)) }
        assertSame(koin.get<BackendClient>().links, koin.get<SubjectLinksApi>())
        assertSame<Any>(koin.get<SubjectLinksRepositoryImpl>(), koin.get<SessionDataCleaner>(named("links")))
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theDemoLinksAnswerAndEveryChangeIsRefusedWithoutARequest() = runTest {
        val koin = graph()
        val links = koin.get<SubjectLinksRepository>()
        val refused = AppResult.Failure(AppError.DemoUnavailable)

        val state = assertIs<SubjectLinksState.Content>(links.observe(SCOPE).first())
        assertTrue(state.snapshot.mine.any { it.id == OWN_LINK }, "Anna's own notes")
        assertTrue(state.snapshot.shared.isNotEmpty())
        assertEquals(AppResult.Success(Unit), links.refresh(SCOPE))
        val save = links.save(
            SCOPE, OWN_LINK, LinkCategory.NOTES, "https://example.org", null, LinkVisibility.PRIVATE, null,
        )
        assertEquals(refused, save)
        assertEquals(refused, links.vote(SCOPE, state.snapshot.shared.first().id, 1))
        assertEquals(refused, links.report(SCOPE, state.snapshot.shared.first().id, ResourceReportReason.SPAM, null))
        assertEquals(emptyList(), requests)
        koin.close()
    }

    private fun arguments(linkId: String? = null) = SavedStateHandle(
        listOfNotNull(
            SubjectLinksArgs.SUBJECT_ID to SCOPE.subjectId,
            SubjectLinksArgs.SUBJECT_NAME to SCOPE.subjectName,
            SubjectLinksArgs.PERIOD_KEY to SCOPE.periodKey,
            linkId?.let { SubjectLinksArgs.LINK_ID to it },
        ).toMap(),
    )

    private fun graph(): Koin = koinApplication {
        allowOverride(true)
        modules(iosCoreModule(FakeHost(), ORIGIN), resourcesModule, resourcesIosModule, platformTypes(), testDevice())
    }.koin

    private fun platformTypes(): Module = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<CurrentUserProvider> {
            object : CurrentUserProvider {
                override suspend fun getCurrentUser(): CurrentUser = DemoPeople.ME
            }
        }
        single<CustomServicesRepository> { FakeCustomServicesRepository(enabled = true) }
    }

    /** A counting engine, directories and the App Group under the test's temporary directory, no main queue. */
    private fun testDevice(): Module = module {
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

    private class FakeHost : IosCoreHost {
        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) = Unit
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
        val SCOPE = ResourceScope(DemoStudy.ALGORITHMS.id, DemoStudy.ALGORITHMS.name, "2026/2027:1")
        /** `DemoSubjectLinks`' fifth link of the algorithms: Anna's private notes. */
        const val OWN_LINK = "00000000-0000-4000-9000-000099020304"
    }
}
