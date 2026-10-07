package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The links sheets' fixture in Koin: a debug host replaces the subject links repository with its own while it lives,
 * so the four links hosts obtain their ViewModels exactly as in release.
 *
 * [repository] is read whenever a ViewModel is created, so a test that swaps the host's repository field before it
 * opens a sheet gets that repository. Koin is process-wide: a host calls [load] in `onCreate` before
 * `super.onCreate()` and [unload] in `onDestroy`, or later tests in the same process would get the fake. Main thread
 * only, like the host callbacks.
 */
object ResourcesDebugFixtures {

    private var current: Module? = null

    /** Overrides the subject links repository; returns the handle [unload] takes. */
    fun load(context: Context, repository: () -> SubjectLinksRepository): Module {
        val fixture = module {
            factory<SubjectLinksRepository> { repository() }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release binding. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode. The contract points back at `resourcesModule`'s single: reloading that module would build a second
     * repository beside the one the session cleaner and the recordbook hold. A fixture that a newer host already
     * replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.declare<SubjectLinksRepository>(koin.get<SubjectLinksRepositoryImpl>(), allowOverride = true)
        current = null
    }
}
