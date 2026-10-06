package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The recordbook screens' fixture in Koin: a debug host replaces every bridged type the recordbook ViewModels read
 * (the recordbook data, sport scores, the schedule gateways, subject links, teacher levels and the academic time)
 * with its own while it lives, so `RecordbookFragment`, `RecordbookSubjectFragment` and `SheetScoresBottomSheet`
 * obtain their ViewModels exactly as in release.
 *
 * Koin is process-wide: a host calls [load] in `onCreate` before `super.onCreate()` and [unload] in `onDestroy`, or
 * later tests in the same process would get the fakes. Main thread only, like the host callbacks.
 */
object RecordbookDebugFixtures {

    /** Hands out the host's fakes; each new ViewModel reads them at that moment, so tests may swap them per launch. */
    interface Fakes {
        fun recordbook(): RecordbookRepository
        fun bars(): BarsRecordbookRepository
        fun barsPreference(): BarsPreferenceRepository
        fun marks(): MarkTrackingRepository
        fun sheets(): SheetScoresRepository
        fun bindings(): SubjectBindingStore
        fun sport(): SportScoreRepository
        fun lessons(): SubjectLessonsGateway
        fun scheduleRefresh(): ScheduleRefreshGateway
        fun links(): SubjectLinksRepository
        fun levels(): TeacherLevelsRepository
        fun time(): AcademicTimeProvider
    }

    /** The bridge modules that define the overridden types, loaded again once the last fixture goes. */
    private val bridgeModules: List<Module>
        get() = listOf(coreBridgeModule, resourcesBridgeModule, reviewsBridgeModule, recordbookBridgeModule)

    private var current: Module? = null

    /** Overrides the bridged types with [fakes]; returns the handle [unload] takes. */
    fun load(context: Context, fakes: Fakes): Module {
        val fixture = module {
            factory<RecordbookRepository> { fakes.recordbook() }
            factory<BarsRecordbookRepository> { fakes.bars() }
            factory<BarsPreferenceRepository> { fakes.barsPreference() }
            factory<MarkTrackingRepository> { fakes.marks() }
            factory<SheetScoresRepository> { fakes.sheets() }
            factory<SubjectBindingStore> { fakes.bindings() }
            factory<SportScoreRepository> { fakes.sport() }
            factory<SubjectLessonsGateway> { fakes.lessons() }
            factory<ScheduleRefreshGateway> { fakes.scheduleRefresh() }
            factory<SubjectLinksRepository> { fakes.links() }
            factory<TeacherLevelsRepository> { fakes.levels() }
            factory<AcademicTimeProvider> { fakes.time() }
        }
        KoinStarter.ensureStarted(context).loadModules(listOf(fixture), allowOverride = true)
        current = fixture
        return fixture
    }

    /**
     * Restores the release bindings. Unloading a Koin module drops its keys instead of bringing back what it
     * overrode, so the bridge modules load again; their singles forward Hilt's instances, so readers get the same
     * objects as before. The recordbook's own definitions were never overridden. A fixture that a newer host already
     * replaced is left to that host.
     */
    fun unload(context: Context, fixture: Module) {
        if (current !== fixture) return
        val koin = KoinStarter.ensureStarted(context)
        koin.unloadModules(listOf(fixture))
        koin.loadModules(bridgeModules, allowOverride = true)
        current = null
    }
}
