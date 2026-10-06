package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.me.presentation.MeViewModel
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The Me tab's and the first-run flow's fixture in Koin: a debug host builds both ViewModels over its own fakes while
 * it lives, so `MeFragment`, `OnboardingFragment` and its step pages obtain them exactly as in release.
 *
 * The ViewModels are overridden rather than their bridged types: the two screens read the custom-services opt-in from
 * different fakes, and the host's other screens keep the release session, social data and opt-in.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fakes. Main thread only, like the host callbacks.
 */
object AccountDebugFixtures {

    /** Builds a ViewModel for each new Fragment, reading the host's fixture at that moment. */
    interface Fakes {
        fun me(): MeViewModel
        fun onboarding(savedStateHandle: SavedStateHandle): OnboardingViewModel
    }

    /** The release modules that define the overridden ViewModels, loaded again once the last fixture goes. */
    private val releaseModules: List<Module> get() = listOf(meModule, onboardingModule)

    private var current: Module? = null

    /** Overrides the Me and onboarding ViewModels; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes): Module {
        val fixture = module {
            viewModel<MeViewModel> { fakes.me() }
            viewModel<OnboardingViewModel> { fakes.onboarding(get()) }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release definitions. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the release modules load again. A fixture that a newer host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(releaseModules, allowOverride = true)
        current = null
    }
}
