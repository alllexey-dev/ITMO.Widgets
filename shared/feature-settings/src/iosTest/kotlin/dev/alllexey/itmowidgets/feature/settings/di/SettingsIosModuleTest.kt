package dev.alllexey.itmowidgets.feature.settings.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.platform.IosCoreHost
import dev.alllexey.itmowidgets.core.platform.IosPlatformCapabilities
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.feature.settings.data.IosWidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.DiagnosticsViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingItem
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPage
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPageState
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsPages
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.Path
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * The settings graph as the iOS app starts it (`IosKoinModules`): the core module, the settings modules and
 * [settingsIosModule], with the account and onboarding types and the simulator test binary's device stood in.
 */
class SettingsIosModuleTest {

    private val root: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "itmo-settings-test-${Random.nextLong().toULong()}"
    private val host = FakeHost()

    @AfterTest
    fun deleteDevice() = FileSystem.SYSTEM.deleteRecursively(root)

    /** Koin's `verify()` is JVM-only; on Kotlin/Native every definition is resolved instead. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun everyDefinitionResolvesAndEveryPageGetsItsOwnViewModel() {
        val koin = graph()
        val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
            // The ViewModels take their page or a port of a later card; they are resolved below.
            .filterNot { it.primaryType.simpleName.orEmpty().endsWith("ViewModel") }

        definitions.forEach { definition -> koin.get<Any>(definition.primaryType, definition.qualifier) }

        SettingsPage.entries.forEach { page ->
            val viewModel = koin.get<SettingsViewModel> { parametersOf(*settingsPageParameters(page.name).toTypedArray()) }
            assertEquals(page, viewModel.uiState.value.page)
        }
        koin.get<DiagnosticsViewModel>()
        assertSame(IosPlatformCapabilities, koin.get<PlatformCapabilities>())
        assertFalse(koin.get<QuickSettingsTileAccess>().canRequestAdd())
        koin.close()
    }

    @Test
    fun anUnknownPageOpensTheRoot() {
        val koin = graph()

        val viewModel = koin.get<SettingsViewModel> { parametersOf(*settingsPageParameters("NOPE").toTypedArray()) }

        assertEquals(SettingsPage.ROOT, viewModel.uiState.value.page)
        koin.close()
    }

    @Test
    fun theRootHidesTheRecordbookPageUntilMarkTrackingShips() {
        val koin = graph()
        val state = SettingsPageState(
            local = LocalSettings(),
            sharing = SharingSettingsState.Disabled,
            notificationsGranted = true,
            hasCustomSpoiler = false,
            imageBusy = false,
            backgroundWorkRestricted = false
        )

        val pages = koin.get<SettingsPages>().forPage(SettingsPage.ROOT).sections(SettingsPage.ROOT, state)
            .flatMap { it.items }
            .filterIsInstance<SettingItem.Navigation>()
            .map { it.page }

        assertFalse(SettingsPage.RECORDBOOK in pages)
        assertTrue(SettingsPage.MAINTENANCE in pages)
        koin.close()
    }

    @Test
    fun refreshingWidgetsReloadsEveryKind() {
        val koin = graph()

        koin.get<WidgetRefreshRequester>().refreshAll()

        assertEquals(IosWidgetRefreshRequester.WIDGET_KINDS, host.reloaded)
        koin.close()
    }

    private fun graph(): Koin = koinApplication {
        allowOverride(true)
        modules(
            iosCoreModule(host, ORIGIN, VERSION),
            settingsDataModule,
            settingsModule,
            settingsIosModule,
            standIns()
        )
    }.koin

    /**
     * The account module's types (`accountIosModule`, `authDataModule`), the onboarding flag, the recordbook graph's
     * mark tracking (`recordbookModule`), the schedule data graph's change tracking and calendar sync
     * (`scheduleDataModule`), the app's directories under [root], and no main queue (the test blocks it).
     */
    private fun standIns(): Module = module {
        single<DemoMode> { FakeDemoMode(active = true) }
        single<BackendIdentitySync> {
            object : BackendIdentitySync {
                override suspend fun sync(scheduleRetry: Boolean) = true
            }
        }
        single<BackendDeviceSession> {
            object : BackendDeviceSession {
                override suspend fun registerCurrentDevice() = Unit

                override suspend fun unregisterCurrentDevice() = Unit
            }
        }
        single<FcmTokenSync> { FcmTokenSync { } }
        single<SessionRepository> { FakeSessionRepository(SessionState.SignedOut) }
        single<OnboardingRepository> { FakeOnboardingRepository(completed = true) }
        single<MarkTracking> { FakeMarkTracking() }
        single<ScheduleChangeTracking> { FakeScheduleChangeTracking() }
        single<CalendarSync> { FakeCalendarSync() }
        single<AppDirectories> {
            object : AppDirectories {
                override val files = root / "files"
                override val cache = root / "caches"
                override val noBackup = root / "no-backup"
            }.also { directories ->
                listOf(directories.files, directories.cache, directories.noBackup)
                    .forEach(FileSystem.SYSTEM::createDirectories)
            }
        }
        single<AppDispatchers> { AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default) }
    }

    private class FakeHost : IosCoreHost {
        val reloaded = mutableListOf<String>()

        override fun topViewController(): UIViewController? = null

        override fun clearWebsiteData(completion: () -> Unit) = completion()

        override fun reload(kind: String) {
            reloaded += kind
        }
    }

    private companion object {
        const val ORIGIN = "https://dev.widgets.alllexey.dev"
        const val VERSION = "2.3-test"
    }
}
