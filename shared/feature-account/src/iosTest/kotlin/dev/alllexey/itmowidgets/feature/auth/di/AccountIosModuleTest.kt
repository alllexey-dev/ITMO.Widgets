package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionSnapshot
import dev.alllexey.itmowidgets.core.session.SessionSnapshotWriter
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/** The shared session on its iOS ports, as `IosKoinModules` loads it: the core module, [authDataModule] and ours. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-account-test-${Random.nextLong().toULong()}"
    private val log = RecordingAppLog()
    private val requests = mutableListOf<String>()

    private val directories = object : AppDirectories {
        override val files = root / "files"
        override val cache = root / "caches"
        override val noBackup = root / "no-backup"
    }
    private val appGroup by lazy {
        AppGroupDirectory.resolve(
            identifiers = BundleIdentifiers(appGroupId = "group.test.itmo", keychainGroup = null),
            appDirectories = directories,
            log = log,
            containerOf = { root / "group" },
        )
    }

    @AfterTest
    fun deleteRoot() = FileSystem.SYSTEM.deleteRecursively(root)

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionResolvesAndTheSessionReadsEveryCleanerOfTheGraph() {
        val koin = graph(iosCoreModule(FakeHost(), ORIGIN), authDataModule, accountIosModule, testDevice())
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        definitions.forEach { definition -> koin.get<Any>(definition.primaryType, definition.qualifier) }

        assertIs<SessionRepositoryImpl>(koin.get<SessionRepository>())
        // The Keychain, the App Group container and WebKit's data (IO-04b); features loaded later add theirs.
        assertEquals(3, koin.get<SessionDataCleaners>().current().size)
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theSessionSnapshotFollowsEverySignedInSessionAndIsWrittenAgainAfterASignOut() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val session = FakeSessionRepository(SessionState.Initializing)
        val writer = SessionSnapshotWriter(AppGroupSnapshotWriter(appGroup, reloader = {}))
        SessionSnapshotSync(session, writer, log).launchIn(backgroundScope)
        assertNull(writer.read())

        session.mutableState.value = SessionState.SignedIn(DemoPeople.ME, demo = true)
        assertEquals(SessionSnapshot(isu = DemoPeople.ME.isu, demo = true, alertsAllowed = false), writer.read())

        // Sign-out: the App Group cleaner removes the file, no state writes it.
        session.mutableState.value = SessionState.SigningOut
        FileSystem.SYSTEM.delete(appGroup.file(SessionSnapshotWriter.FILE.fileName))
        session.mutableState.value = SessionState.SignedOut
        assertNull(writer.read())

        session.mutableState.value = SessionState.SignedIn(DemoPeople.ME, demo = true)
        assertEquals(SessionSnapshot(isu = DemoPeople.ME.isu, demo = true, alertsAllowed = false), writer.read())

        session.mutableState.value = SessionState.SignedIn(CurrentUser(isu = ISU, name = null, pictureUrl = null))
        assertEquals(SessionSnapshot(isu = ISU, demo = false, alertsAllowed = false), writer.read())
    }

    /** A counting engine, the test's own directories and App Group, and no main queue (the test blocks it). */
    private fun testDevice() = module {
        single<HttpClientEngine> {
            MockEngine { request ->
                requests += request.url.toString()
                respondError(HttpStatusCode.ServiceUnavailable)
            }
        }
        single<AppDirectories> { directories }
        single<AppGroupDirectory> { appGroup }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
        single<AppLog> { log }
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
        const val ISU = 123456
    }
}
