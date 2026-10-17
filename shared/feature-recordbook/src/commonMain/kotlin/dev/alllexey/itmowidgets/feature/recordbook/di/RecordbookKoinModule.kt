package dev.alllexey.itmowidgets.feature.recordbook.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.lesson.LessonSubjectSource
import dev.alllexey.itmowidgets.core.today.TodayTodoSource
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsSessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsBackgroundLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsCookieSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRenewal
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OwnerBoundBarsStorage
import dev.alllexey.itmowidgets.feature.recordbook.data.bell.MarksBellSource
import dev.alllexey.itmowidgets.feature.recordbook.data.lesson.RecordbookLessonSubjects
import dev.alllexey.itmowidgets.feature.recordbook.data.today.BarsTodoSource
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.BarsMarksActivation
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksFileStore
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.PublicSheetClient
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresFileStore
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLinksLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacherLevelsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Ktor engine of BARS and its ITMO.ID login, which the platform supplies: on Android the app's OkHttp engine with
 * the BARS timeouts and no cookie jar, cache or redirects. It is never the MyITMO engine, so no cookie or connection
 * state is shared with MyITMO.
 */
val barsEngineQualifier: Qualifier = named("bars")

/** The marks' place in the open sets of `BellSource`s and `TodayTodoSource`s (the BARS sign-in) Сегодня reads. */
val marksQualifier: Qualifier = named("marks")

/**
 * The recordbook: the screens (four ViewModels, the subject page's loaders, two resolvers) and all of its data:
 * MyITMO and BARS, mark tracking, the public sheets and the subject bindings. Koin is the only graph for the data
 * below.
 *
 * From the platform (`androidRecordbookModule` on Android, `recordbookIosModule` on iOS): the BARS engine
 * ([barsEngineQualifier]), the ITMO.ID sign-in of the `bars` client over it (`BarsLogin`), the WebView silent login
 * (`BarsSilentLogin`), the ITMO.ID cookies (`ItmoIdCookies`), the marks worker's scheduler (`MarksScheduler`) and its
 * notifications (`MarksNotifier`). From the platform's other Koin modules: the core contracts (`MyItmoClient`, the
 * `app_preferences` DataStore, `AppDirectories`, `MarkSourcePreferences`, `SecureStore`, `SessionTokenStore`,
 * `CurrentUserProvider`, `AppNotifier`, time, `DemoMode`, `AppDispatchers`), subject links and teacher levels.
 */
val recordbookModule = module {
    // Stateless helpers: every ViewModel gets its own.
    factoryOf(::SubjectContextResolver)
    factoryOf(::RecordbookSportResolver)
    // A loader keeps per-page state (the binding version, the stable link order), so each subject page owns one.
    factoryOf(::SubjectLessonsLoader)
    factoryOf(::SubjectLinksLoader)
    factoryOf(::SubjectSheetLoader)
    factoryOf(::SubjectTeacherLevelsLoader)
    viewModelOf(::RecordbookViewModel)
    viewModelOf(::RecordbookSubjectViewModel)
    viewModelOf(::SheetScoresViewModel)
    viewModelOf(::BarsLoginViewModel)

    // MyITMO: one memory cache for the screens, the marks check and sign-out. Cleaner contributions are qualified
    // (an open set that sign-out reads).
    singleOf(::RecordbookRepositoryImpl) { bind<RecordbookRepository>() }
    single<SessionDataCleaner>(named("recordbook")) { get<RecordbookRepositoryImpl>() }

    // BARS: exactly one session store and one client (which builds the one library client) per process. Two of
    // either would mean two session locks and two writers of `bars_tokens.enc`.
    singleOf(::BarsTokenStore)
    singleOf(::OwnerBoundBarsStorage)
    singleOf(::BarsCookieSilentLogin) { bind<BarsBackgroundLogin>() }
    singleOf(::BarsRenewal)
    single<BarsClient> {
        BarsClient(get<HttpClientEngine>(barsEngineQualifier), get(), get(), get(), get(), get(), get())
    }
    singleOf(::BarsMarkReader) { bind<BarsMarkSource>() }
    singleOf(::BarsSessionRepositoryImpl) { bind<BarsSessionRepository>() }
    singleOf(::BarsRecordbookRepositoryImpl) { bind<BarsRecordbookRepository>() }
    single<SessionDataCleaner>(named("recordbook-bars")) { get<BarsRecordbookRepositoryImpl>() }
    singleOf(::BarsPreferenceRepositoryImpl) { bind<BarsPreferenceRepository>() }
    single<SessionDataCleaner>(named("recordbook-bars-preference")) { get<BarsPreferenceRepositoryImpl>() }
    // A good BARS answer turns "Оценки БАРС" on; the one BarsClient calls it.
    singleOf(::BarsMarksActivation) { bind<BarsSessionListener>() }

    // Subject bindings in `app_preferences` (`subject_bindings`).
    singleOf(::DataStoreSubjectBindingStore) { bind<SubjectBindingStore>() }
    single<SessionDataCleaner>(named("recordbook-subject-bindings")) { get<DataStoreSubjectBindingStore>() }

    // Public sheets: one Ktor client for the process, owned by the client single; `filesDir/sheet_scores`.
    singleOf<PublicSheetClient, DemoMode, AppDispatchers>(::PublicSheetClient)
    singleOf<SheetScoresFileStore, AppDirectories>(::SheetScoresFileStore)
    singleOf(::SheetScoresRepositoryImpl) { bind<SheetScoresRepository>() }
    single<SessionDataCleaner>(named("recordbook-sheets")) { get<SheetScoresRepositoryImpl>() }

    // Mark tracking: one state lock per process for the screens, the worker and sign-out; `filesDir/marks`.
    singleOf<MarksFileStore, AppDirectories>(::MarksFileStore)
    singleOf(::MarkTrackingRepositoryImpl) { bind<MarkTrackingRepository>() }
    single<SessionDataCleaner>(named("recordbook-marks")) { get<MarkTrackingRepositoryImpl>() }
    // The switches; the app also binds this one instance as a background check.
    singleOf(::DefaultMarkTracking) { bind<MarkTracking>() }
    // One background run; stateless.
    factoryOf(::MarksCheck)
    // The bell's marks and the BARS sign-in under the day, each under its qualifier in the open sets Сегодня reads.
    single<BellSource>(marksQualifier) { MarksBellSource(get(), get()) }
    single<TodayTodoSource>(marksQualifier) { BarsTodoSource(get(), get(), get()) }
    // The lesson sheet's `Предмет` and `Ссылки 3`, in the open set of `LessonSubjectSource`s it reads.
    single<LessonSubjectSource>(named("recordbook")) { RecordbookLessonSubjects(get(), get(), get(), get(), get()) }
}
