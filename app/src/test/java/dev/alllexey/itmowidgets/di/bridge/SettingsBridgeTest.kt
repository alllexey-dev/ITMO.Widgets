package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SchedulePreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.WidgetAppearanceRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SettingsBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the Android side of settings resolves in Koin to what Hilt builds`() {
        val application = bootApplication()
        val hilt = SettingsBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.widgetRefreshRequester(), koin.get<WidgetRefreshRequester>())
        assertEquals(hilt.appVersion(), koin.get<AppVersion>())
        assertEquals(hilt.backgroundWorkAccess()::class, koin.get<BackgroundWorkAccess>()::class)
        assertEquals(hilt.quickSettingsTileAccess()::class, koin.get<QuickSettingsTileAccess>()::class)
    }

    @Test
    fun `each settings repository is one Koin single that Hilt readers share`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<SettingsRepositoryImpl>(), koin.get<SettingsRepository>())
        assertSame(koin.get<CustomServicesRepositoryImpl>(), koin.get<CustomServicesRepository>())
        assertSame(koin.get<WidgetAppearanceRepositoryImpl>(), koin.get<WidgetAppearanceRepository>())
        assertSame(koin.get<SchedulePreferencesRepositoryImpl>(), koin.get<SchedulePreferencesRepository>())

        assertSame(koin.get<CustomServicesRepository>(), SettingsBridge.customServicesRepository(application))
        assertSame(koin.get<WidgetAppearanceRepository>(), SettingsBridge.widgetAppearanceRepository(application))
        assertSame(koin.get<SchedulePreferencesRepository>(), SettingsBridge.schedulePreferencesRepository(application))
    }

    /** Without an `@Inject` constructor Hilt cannot build a second instance beside Koin's: one graph per binding. */
    @Test
    fun `Hilt cannot construct the moved repositories`() {
        listOf(
            SettingsRepositoryImpl::class.java,
            CustomServicesRepositoryImpl::class.java,
            WidgetAppearanceRepositoryImpl::class.java,
            SchedulePreferencesRepositoryImpl::class.java,
        ).forEach { type ->
            assertFalse(type.name, type.constructors.any { it.isAnnotationPresent(Inject::class.java) })
        }
    }

    @Test
    fun `the settings modules pass the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(
            KoinModules.bridges,
            listOf(onboardingDataModule, settingsModule) + scheduleDataGraph,
        )
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
