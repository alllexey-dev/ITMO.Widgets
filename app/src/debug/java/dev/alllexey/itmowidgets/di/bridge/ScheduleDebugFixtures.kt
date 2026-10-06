package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.LessonFriendsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.changes.ScheduleChangesRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.repository.ScheduleRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.settings.data.CustomServicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.settings.data.SchedulePreferencesRepositoryImpl
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The schedule screens' fixture in Koin: a debug host replaces everything the three schedule ViewModels read with its
 * own fakes while it lives, so `ScheduleFragment`, `LessonDetailsBottomSheet` and `ScheduleChangesFragment` obtain
 * their ViewModels exactly as in release. Nothing reaches the network, the session or the files.
 *
 * Koin is process-wide: a host calls [load] or [loadChanges] in `onCreate` before `super.onCreate()` and [unload] in
 * `onDestroy`, or later tests in the same process would get the fakes. Main thread only, like the host callbacks.
 */
object ScheduleDebugFixtures {

    /** Builds the fakes for each new ViewModel, reading the host's fixture at that moment. */
    interface Fakes {
        fun time(): AcademicTimeProvider
        fun schedule(): ScheduleRepository
        fun changes(): ScheduleChangesRepository
        fun preferences(): SchedulePreferencesRepository
        fun pendingSport(): PendingSportBookingsRepository
        fun calendarSync(): CalendarSync
        fun lessonFriends(): LessonFriendsRepository
        fun customServices(): CustomServicesRepository
        fun teacherLevels(): TeacherLevelsRepository
    }

    /** The bridge modules that define the overridden types, loaded again once the last fixture goes. */
    private val bridgeModules: List<Module> get() = listOf(coreBridgeModule, reviewsBridgeModule)

    private var current: Module? = null

    /** Overrides every type the schedule list and the lesson sheet read; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes): Module = install(context, fixture(fakes::time, fakes::changes, fakes))

    /** Overrides only what the change history reads; returns the handle [unload] takes. */
    fun loadChanges(context: Context, changes: ScheduleChangesRepository, time: AcademicTimeProvider): Module =
        install(context, fixture({ time }, { changes }, screens = null))

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the bridge modules load again; their singles forward Hilt's instances, as before. Koin owns the
     * opt-in and the schedule preferences (`settingsDataModule`) and the schedule data (`scheduleDataModule`): loading
     * those modules again would build second repositories, so the contracts point back at their singles, which the
     * fixture never overrode. A fixture that a newer host already replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(bridgeModules, allowOverride = true)
        koin.declare<CustomServicesRepository>(koin.get<CustomServicesRepositoryImpl>(), allowOverride = true)
        koin.declare<SchedulePreferencesRepository>(koin.get<SchedulePreferencesRepositoryImpl>(), allowOverride = true)
        koin.declare<ScheduleRepository>(koin.get<ScheduleRepositoryImpl>(), allowOverride = true)
        koin.declare<ScheduleChangesRepository>(koin.get<ScheduleChangesRepositoryImpl>(), allowOverride = true)
        koin.declare<LessonFriendsRepository>(koin.get<LessonFriendsRepositoryImpl>(), allowOverride = true)
        current = null
    }

    private fun fixture(
        time: () -> AcademicTimeProvider,
        changes: () -> ScheduleChangesRepository,
        screens: Fakes?,
    ): Module = module {
        factory<AcademicTimeProvider> { time() }
        factory<ScheduleChangesRepository> { changes() }
        if (screens != null) {
            factory<ScheduleRepository> { screens.schedule() }
            factory<SchedulePreferencesRepository> { screens.preferences() }
            factory<PendingSportBookingsRepository> { screens.pendingSport() }
            factory<CalendarSync> { screens.calendarSync() }
            factory<LessonFriendsRepository> { screens.lessonFriends() }
            factory<CustomServicesRepository> { screens.customServices() }
            factory<TeacherLevelsRepository> { screens.teacherLevels() }
        }
    }

    private fun install(context: Context, fixture: Module): Module {
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }
}
