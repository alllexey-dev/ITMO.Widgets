package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The web sign-in sheet's fixture in Koin: a debug host builds the sheet's ViewModel over its own answers and clock
 * while it lives, so `WebLoginBottomSheet` obtains it exactly as in release and nothing reaches Backend.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fixture. Main thread only, like the host callbacks.
 */
object AccountWebLoginDebugFixtures {

    private var current: Module? = null

    /** Overrides the sheet's ViewModel, reading [repository] for each new sheet; returns the handle [unload] takes. */
    fun load(context: Context, repository: () -> WebLoginRepository, time: AcademicTimeProvider): Module {
        val fixture = module { viewModel<WebLoginViewModel> { WebLoginViewModel(get(), repository(), time) } }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release definition. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the release module loads again. A fixture that a newer host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(listOf(webLoginModule), allowOverride = true)
        current = null
    }
}
