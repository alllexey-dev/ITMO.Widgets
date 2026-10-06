package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.settings.presentation.CustomSpoilerViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.IcsExportViewModel
import dev.alllexey.itmowidgets.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The settings screens' fixture in Koin: a debug host builds the settings, custom spoiler and `.ics` ViewModels over
 * its in-memory fakes while it lives, and `SettingsFragment` and `IcsExportBottomSheet` obtain them through Koin as
 * in release, with the `SavedStateHandle` Koin makes from the Fragment's arguments. The ViewModels are overridden
 * rather than the core contracts behind them, which are process-wide and read by other screens of the same host.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fake. Main thread only, like the host callbacks.
 */
object SettingsDebugFixtures {

    /** Builds a new ViewModel over the host's fakes for each Fragment that asks. */
    interface Fakes {
        fun settingsViewModel(savedStateHandle: SavedStateHandle): SettingsViewModel
        fun customSpoilerViewModel(): CustomSpoilerViewModel
        fun icsExportViewModel(savedStateHandle: SavedStateHandle): IcsExportViewModel
    }

    /** The release module that defines the overridden ViewModels, loaded again once the last fixture goes. */
    private val releaseModules: List<Module> get() = listOf(settingsModule)

    private var current: Module? = null

    /** Overrides the three ViewModels; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes): Module {
        val fixture = module {
            viewModel<SettingsViewModel> { fakes.settingsViewModel(get()) }
            viewModel<CustomSpoilerViewModel> { fakes.customSpoilerViewModel() }
            viewModel<IcsExportViewModel> { fakes.icsExportViewModel(get()) }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release definitions. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the release module loads again. A fixture that a newer host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(releaseModules, allowOverride = true)
        current = null
    }
}
