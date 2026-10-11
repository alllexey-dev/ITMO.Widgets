package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.AndroidWidgetPaletteSource
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.settings.data.AndroidBackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.data.AndroidQuickSettingsTileAccess
import org.junit.Assert.assertTrue
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SchedulePreferencesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SettingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.WidgetAppearanceRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
class SettingsGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the Android side of settings resolves in Koin`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<WidgetRefreshRequester>(), koin.get<WidgetRefreshRequester>())
        assertEquals(AppVersion(application.getString(R.string.app_version)), koin.get<AppVersion>())
        assertTrue(koin.get<BackgroundWorkAccess>() is AndroidBackgroundWorkAccess)
        assertTrue(koin.get<QuickSettingsTileAccess>() is AndroidQuickSettingsTileAccess)
        assertTrue(koin.get<WidgetPaletteSource>() is AndroidWidgetPaletteSource)
    }

    @Test
    fun `each settings repository is one Koin single`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<SettingsRepositoryImpl>(), koin.get<SettingsRepository>())
        assertSame(koin.get<CustomServicesRepositoryImpl>(), koin.get<CustomServicesRepository>())
        assertSame(koin.get<WidgetAppearanceRepositoryImpl>(), koin.get<WidgetAppearanceRepository>())
        assertSame(koin.get<SchedulePreferencesRepositoryImpl>(), koin.get<SchedulePreferencesRepository>())
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin is up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
