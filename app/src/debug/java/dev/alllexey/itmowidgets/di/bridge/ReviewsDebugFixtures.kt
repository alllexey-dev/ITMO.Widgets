package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherLessonsGatewayImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The review editor's and report dialog's fixture in Koin: a debug host replaces the bridged reviews repository and
 * teacher lessons gateway with its own while it lives, so both hosts obtain their ViewModels exactly as in release.
 *
 * [reviews] and [lessons] are read whenever a ViewModel is created. Koin is process-wide: a host calls [load] in
 * `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or later tests in the same process would get the
 * fakes. Main thread only, like the host callbacks.
 */
object ReviewsDebugFixtures {

    /** The release modules that define the overridden types, loaded again once the last fixture goes. */
    private val releaseModules: List<Module> get() = listOf(coreBridgeModule, reviewsBridgeModule)

    private var current: Module? = null

    /** Overrides the reviews repository and the lessons gateway; returns the handle [unload] takes. */
    fun load(context: Context, reviews: () -> TeacherReviewsRepository, lessons: () -> TeacherLessonsGateway): Module {
        val fixture = module {
            factory<TeacherReviewsRepository> { reviews() }
            factory<TeacherLessonsGateway> { lessons() }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the bridges load again; their singles forward Hilt's instances, the ones the session cleaners
     * hold. The lessons gateway is Koin's own (`scheduleDataModule`): loading that module again would build a second
     * gateway beside the session cleaner's, so the contract points back at its single. A fixture that a newer host
     * already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(releaseModules, allowOverride = true)
        koin.declare<TeacherLessonsGateway>(koin.get<TeacherLessonsGatewayImpl>(), allowOverride = true)
        current = null
    }
}
