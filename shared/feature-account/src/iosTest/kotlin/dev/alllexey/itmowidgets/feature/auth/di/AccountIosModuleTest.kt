package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.notification.PushDeviceRegistration
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.DefaultBackendIdentitySync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionSnapshot
import dev.alllexey.itmowidgets.core.session.SessionSnapshotWriter
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.InstallIdPreferences
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.core.text.UiTextResolver
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import dev.alllexey.itmowidgets.feature.auth.data.SessionRepositoryImpl
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import dev.alllexey.itmowidgets.feature.update.di.AppUpdateIosParameters
import dev.alllexey.itmowidgets.feature.update.di.updateIosModule
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.feature.weblogin.data.PairingHost
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginDataModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginIosModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.Foundation.NSData
import platform.UIKit.UIViewController

/**
 * The shared session on its iOS ports, as `IosKoinModules` loads it: the core module, [authDataModule] and ours, with
 * the web sign-in and the update offer on theirs.
 */
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
        val koin = accountGraph()
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()

        // The update screen's arguments satisfy both ViewModels that take a SavedStateHandle; the rest ignore them.
        val parameters = AppUpdateIosParameters.viewModel(SAMPLE_UPDATE).toTypedArray()
        definitions.forEach { definition ->
            koin.get<Any>(definition.primaryType, definition.qualifier) { parametersOf(*parameters) }
        }

        assertIs<SessionRepositoryImpl>(koin.get<SessionRepository>())
        assertIs<DefaultBackendIdentitySync>(koin.get<BackendIdentitySync>())
        // The Keychain, the App Group container, WebKit's data and the X-Client install id; features loaded later add
        // theirs.
        assertEquals(4, koin.get<SessionDataCleaners>().current().size)
        assertTrue(koin.get<SessionDataCleaners>().current().any { it === koin.get<InstallIdPreferences>() })
        // One push registration answers the session's token sync and device session.
        val registration = koin.get<PushDeviceRegistration>()
        assertSame<Any>(registration, koin.get<FcmTokenSync>())
        assertSame<Any>(registration, koin.get<BackendDeviceSession>())
        assertEquals(emptyList(), requests)
        koin.close()
    }

    @Test
    fun theIosGraphUploadsTheStoredIdentityThroughBackend() = runTest {
        val now = Instant.parse("2026-10-15T10:00:00Z")
        val stored = TokenSet("access", now + 1.hours, "refresh", now + 2.hours, "synthetic.id.token")
        val engine = MockEngine { respondJson("""{"success":true,"data":"Successfully updated","error":null}""") }
        val koin = accountGraph(module {
            single<Clock> { object : Clock { override fun now(): Instant = now } }
            single<TokenStorage> {
                object : TokenStorage {
                    override suspend fun read(): TokenSet = stored
                    override suspend fun write(tokens: TokenSet?) = Unit
                }
            }
            single<HttpClientEngine> { engine }
            single<BackendGate> { FakeBackendGate(optedIn = true) }
            single<DemoMode> { FakeDemoMode() }
        })
        try {
            assertTrue(koin.get<BackendIdentitySync>().sync())
            val request = engine.requestHistory.single()
            assertEquals("/api/users/me/id-token", request.url.encodedPath)
            assertEquals("Bearer access", request.headers["Authorization"])
            assertEquals("""{"idToken":"synthetic.id.token"}""", request.bodyText())
        } finally {
            koin.close()
        }
    }

    @Test
    fun theUpdateCheckAsksForTheIosRelease() {
        val koin = accountGraph()

        assertEquals(DevicePlatform.IOS, koin.get<DevicePlatform>())
        koin.close()
    }

    @Test
    fun theUpdateCheckSendsNothingWithoutTheOptIn() = runTest(UnconfinedTestDispatcher()) {
        val koin = accountGraph()

        assertNull(koin.get<PendingAppUpdate>().invoke())
        assertEquals(emptyList(), requests)
        koin.close()
    }

    private fun accountGraph(vararg overrides: Module): Koin = graph(
        iosCoreModule(FakeHost(), ORIGIN), authDataModule, accountIosModule,
        webLoginDataModule, webLoginModule, webLoginIosModule(NoPairingHost), updateModule, updateIosModule,
        testDevice(), *overrides,
    )

    /** Pairing's Swift host; resolving the graph calls none of it. */
    private object NoPairingHost : PairingHost {
        override fun hpkeSeal(recipientPublicKey: NSData, info: NSData, plaintext: NSData): NSData? = null

        override fun canUnlockDevice(): Boolean = false

        override fun unlockDevice(reason: String, completion: (Boolean) -> Unit) = completion(false)
    }

    @Test
    fun theSessionSnapshotFollowsEverySignedInSessionAndIsWrittenAgainAfterASignOut() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val session = FakeSessionRepository(SessionState.Initializing)
        val writer = SessionSnapshotWriter(AppGroupSnapshotWriter(appGroup, reloader = {}))
        SessionSnapshotSync(
            session, MutableStateFlow(false), MutableStateFlow(false), MutableStateFlow(emptySet()), { INSTALL_ID }, writer, log,
        )
            .launchIn(backgroundScope)
        assertNull(writer.read())

        session.mutableState.value = SessionState.SignedIn(DemoPeople.ME, demo = true)
        assertEquals(
            SessionSnapshot(isu = DemoPeople.ME.isu, demo = true, alertsAllowed = false, servicesEnabled = false),
            writer.read()
        )

        // Sign-out: the App Group cleaner removes the file, no state writes it.
        session.mutableState.value = SessionState.SigningOut
        FileSystem.SYSTEM.delete(appGroup.file(SessionSnapshotWriter.FILE.fileName))
        session.mutableState.value = SessionState.SignedOut
        assertNull(writer.read())

        session.mutableState.value = SessionState.SignedIn(DemoPeople.ME, demo = true)
        assertEquals(
            SessionSnapshot(isu = DemoPeople.ME.isu, demo = true, alertsAllowed = false, servicesEnabled = false),
            writer.read()
        )

        session.mutableState.value = SessionState.SignedIn(CurrentUser(isu = ISU, name = null, pictureUrl = null))
        assertEquals(
            SessionSnapshot(
                isu = ISU, demo = false, alertsAllowed = false, servicesEnabled = false, installId = INSTALL_ID
            ),
            writer.read()
        )
    }

    @Test
    fun theSessionSnapshotFollowsTheAlertsAnswerTheServicesOptInAndThePushSwitches() = runTest(UnconfinedTestDispatcher()) {
        val session = FakeSessionRepository(SessionState.Initializing)
        val alerts = MutableStateFlow(false)
        val services = MutableStateFlow(false)
        val disabled = MutableStateFlow(emptySet<String>())
        val writer = SessionSnapshotWriter(AppGroupSnapshotWriter(appGroup, reloader = {}))
        SessionSnapshotSync(session, alerts, services, disabled, { INSTALL_ID }, writer, log).launchIn(backgroundScope)

        // Signed out: no file, whatever the answer.
        alerts.value = true
        assertNull(writer.read())

        session.mutableState.value = SessionState.SignedIn(CurrentUser(isu = ISU, name = null, pictureUrl = null))
        assertEquals(
            SessionSnapshot(
                isu = ISU, demo = false, alertsAllowed = true, servicesEnabled = false, installId = INSTALL_ID
            ),
            writer.read()
        )

        alerts.value = false
        assertEquals(
            SessionSnapshot(
                isu = ISU, demo = false, alertsAllowed = false, servicesEnabled = false, installId = INSTALL_ID
            ),
            writer.read()
        )

        services.value = true
        assertEquals(
            SessionSnapshot(
                isu = ISU, demo = false, alertsAllowed = false, servicesEnabled = true, installId = INSTALL_ID
            ),
            writer.read()
        )

        disabled.value = setOf("sport", "friends")
        assertEquals(
            SessionSnapshot(
                isu = ISU,
                demo = false,
                alertsAllowed = false,
                servicesEnabled = true,
                disabledPushes = listOf("friends", "sport"),
                installId = INSTALL_ID,
            ),
            writer.read()
        )
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
        // The app binds it in iosBackgroundModule, which needs the system notification centre.
        single { UiTextResolver { it.resolve() } }
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
        const val INSTALL_ID = "0b6c3c2e-5f1a-4d8e-9a7b-2c4d6e8f0a1b"
        val SAMPLE_UPDATE = AppUpdate(AppVersionName("2.3.0"), AppVersionName("2.4.0"), note = "", unsupported = false)
    }
}
