package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.settings.domain.BackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.domain.QuickSettingsTileAccess
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.feature.settings.presentation.AppVersion
import org.junit.Assert.assertEquals
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
    fun `the settings data resolves in Koin to what Hilt builds`() {
        val application = bootApplication()
        val hilt = SettingsBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.settingsRepository(), koin.get<SettingsRepository>())
        assertSame(hilt.widgetRefreshRequester(), koin.get<WidgetRefreshRequester>())
        assertEquals(hilt.appVersion(), koin.get<AppVersion>())
        assertEquals(hilt.backgroundWorkAccess()::class, koin.get<BackgroundWorkAccess>()::class)
        assertEquals(hilt.quickSettingsTileAccess()::class, koin.get<QuickSettingsTileAccess>()::class)
    }

    @Test
    fun `the settings module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(settingsModule))
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
