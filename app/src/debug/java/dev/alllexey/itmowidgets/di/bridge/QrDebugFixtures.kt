package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import kotlin.time.Clock
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The QR pass screen's fixture in Koin: a debug host replaces the bridged repository and the wall clock with its own
 * while it lives, so `QrCodeFragment` obtains its ViewModel exactly as in release.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process (`QrTileFlowTest`) would get the fake. Main thread only, like the host callbacks.
 */
object QrDebugFixtures {

    /** The release module that defines the overridden clock, loaded again once the last fixture goes. */
    private val releaseModules: List<Module> get() = listOf(coreBridgeModule)

    private var current: Module? = null

    /** Overrides the repository and the wall clock; returns the handle [unload] takes. */
    fun load(context: Context, repository: QrCodeRepository, clock: Clock): Module {
        val fixture = module {
            single<QrCodeRepository> { repository }
            single<Clock> { clock }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the release bindings come back: the core bridge forwards Hilt's singletons and the repository is
     * `qrModule`'s one instance, as before. A fixture that a newer host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(releaseModules, allowOverride = true)
        // Reloading `qrModule` would build a second repository and a second cached pass beside the one the widget
        // already holds, so the repository key points at `qrModule`'s instance again.
        koin.declare<QrCodeRepository>(koin.get<QrCodeRepositoryImpl>(), allowOverride = true)
        current = null
    }
}
