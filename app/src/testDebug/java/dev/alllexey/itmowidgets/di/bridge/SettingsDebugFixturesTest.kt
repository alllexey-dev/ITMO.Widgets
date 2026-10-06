package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

/**
 * Which module's definition Koin resolves for each settings ViewModel while a host lives and after it goes. The
 * definitions are compared, not called, so no ViewModel is built outside a Fragment.
 */
@OptIn(KoinInternalApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SettingsDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture replaces the settings ViewModels until its host unloads it`() {
        val application = bootApplication()
        assertResolvedFrom(settingsModule)

        val fixture = SettingsDebugFixtures.load(application, Fakes)
        assertEquals(3, fixture.mappings.size)
        assertResolvedFrom(fixture)

        SettingsDebugFixtures.unload(application, fixture)
        assertResolvedFrom(settingsModule)
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()

        val first = SettingsDebugFixtures.load(application, Fakes)
        val second = SettingsDebugFixtures.load(application, Fakes)
        SettingsDebugFixtures.unload(application, first)
        assertResolvedFrom(second)

        SettingsDebugFixtures.unload(application, second)
        assertResolvedFrom(settingsModule)
    }

    /** Every ViewModel the fixture overrides resolves to [module]'s definition. */
    private fun assertResolvedFrom(module: Module) {
        val registry = GlobalContext.get().instanceRegistry.instances
        val keys = module.mappings.filterValues { it.beanDefinition.primaryType in viewModels }.keys
        assertEquals(viewModels.size, keys.size)
        keys.forEach { key -> assertSame(key, module.mappings.getValue(key), registry[key]) }
    }

    private val viewModels = setOf(SettingsViewModel::class, CustomSpoilerViewModel::class, IcsExportViewModel::class)

    private object Fakes : SettingsDebugFixtures.Fakes {
        override fun settingsViewModel(savedStateHandle: SavedStateHandle): SettingsViewModel = error("not built")
        override fun customSpoilerViewModel(): CustomSpoilerViewModel = error("not built")
        override fun icsExportViewModel(savedStateHandle: SavedStateHandle): IcsExportViewModel = error("not built")
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
