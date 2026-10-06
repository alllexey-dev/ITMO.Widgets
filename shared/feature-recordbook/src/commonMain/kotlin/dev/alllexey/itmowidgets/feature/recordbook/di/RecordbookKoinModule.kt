package dev.alllexey.itmowidgets.feature.recordbook.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsSessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsBackgroundLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsCookieSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRenewal
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OwnerBoundBarsStorage
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.presentation.BarsLoginViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookSubjectViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.RecordbookViewModel
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLessonsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectLinksLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectSheetLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacherLevelsLoader
import dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets.SheetScoresViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

/**
 * The Ktor engine of BARS and its ITMO.ID login, which the platform supplies: on Android the app's OkHttp engine with
 * the BARS timeouts and no cookie jar, cache or redirects (ADR 0012). It is never the MyITMO engine, so no cookie or
 * connection state is shared with MyITMO.
 */
val barsEngineQualifier: Qualifier = named("bars")

/**
 * The recordbook: the screens (four ViewModels, the subject page's loaders, two resolvers) and the MyITMO and BARS
 * data. Koin is the only graph for the data below; the app's `RecordbookBridge` hands the instances Hilt-built code
 * still takes (mark tracking and the marks check) to Hilt.
 *
 * From the platform: the BARS engine ([barsEngineQualifier]), the ITMO.ID sign-in of the `bars` client over it
 * (`BarsLogin`), the WebView silent login (`BarsSilentLogin`), the ITMO.ID cookies (`ItmoIdCookies`) and the BARS
 * answer listener (`BarsSessionListener`). From the app's other bridges: mark tracking, sheet scores and subject
 * bindings (still Hilt's until KM-11b2), the core contracts
 * (`MyItmoClient`, the `app_preferences` DataStore, `MarkSourcePreferences`, `SecureStore`, `CurrentUserProvider`,
 * time, `DemoMode`, `AppDispatchers`), subject links and teacher levels.
 */
val recordbookModule = module {
    // Stateless helpers: every ViewModel gets its own, as with Hilt's unscoped constructors before.
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
    // (an open set the app merges into Hilt's sign-out set).
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
}
