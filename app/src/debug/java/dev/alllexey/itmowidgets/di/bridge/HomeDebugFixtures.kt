package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The home feed's fixture in Koin: a debug host replaces the bridged feed source, the card preferences, the hint
 * store and the wall clock with its own while it lives, so `HomeFragment` obtains its ViewModel exactly as in release.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fake. Main thread only, like the host callbacks.
 */
object HomeDebugFixtures {

    /** Builds a fresh set of fakes for each new `HomeViewModel`, reading the host's fixture at that moment. */
    interface Fakes {
        fun source(): HomeCardSource
        fun preferences(): HomeCardPreferences
        fun hintStore(): HomeHintStore
    }

    /** The release modules that define the overridden types, loaded again once the last fixture goes. */
    private val releaseModules: List<Module> get() = listOf(coreBridgeModule, homeBridgeModule)

    private var current: Module? = null

    /** Overrides the feed's bridged types and the wall clock; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes, clock: Clock): Module {
        val fixture = module {
            factory<HomeCardSource> { fakes.source() }
            factory<HomeCardPreferences> { fakes.preferences() }
            factory<HomeHintStore> { fakes.hintStore() }
            single<Clock> { clock }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the release modules load again; their singles forward Hilt's instances. A fixture that a newer
     * host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(releaseModules, allowOverride = true)
        current = null
    }
}
