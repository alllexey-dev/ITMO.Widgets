package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsClient
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsHttp
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import io.ktor.client.engine.HttpClientEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the recordbook takes from `:app`: the data Hilt still constructs (mark tracking, sheet scores,
 * subject bindings; KM-11b2 moves them) and the Android side of BARS (the engine and its ITMO.ID sign-in `BarsLogin`,
 * the WebView silent login, the ITMO.ID cookies and the BARS answer listener, `BarsMarksActivation`). Each one is the
 * instance Hilt hands out, so Koin and Hilt readers share it (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecordbookBridgeEntryPoint {
    fun markTrackingRepository(): MarkTrackingRepository
    fun sheetScoresRepository(): SheetScoresRepository
    fun subjectBindingStore(): SubjectBindingStore
    fun barsSilentLogin(): BarsSilentLogin
    fun itmoIdCookies(): ItmoIdCookies
    fun barsSessionListener(): BarsSessionListener
    fun barsLogin(): BarsLogin

    @BarsHttp
    fun barsEngine(): HttpClientEngine

    companion object {
        fun from(context: Context): RecordbookBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, RecordbookBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val recordbookBridgeModule = module {
    single<MarkTrackingRepository> { RecordbookBridgeEntryPoint.from(androidContext()).markTrackingRepository() }
    single<SheetScoresRepository> { RecordbookBridgeEntryPoint.from(androidContext()).sheetScoresRepository() }
    single<SubjectBindingStore> { RecordbookBridgeEntryPoint.from(androidContext()).subjectBindingStore() }
    // Unscoped in Hilt and stateless: Koin keeps the first instance it gets, which the one BarsRenewal holds.
    single<BarsSilentLogin> { RecordbookBridgeEntryPoint.from(androidContext()).barsSilentLogin() }
    single<ItmoIdCookies> { RecordbookBridgeEntryPoint.from(androidContext()).itmoIdCookies() }
    single<BarsSessionListener> { RecordbookBridgeEntryPoint.from(androidContext()).barsSessionListener() }
    single<BarsLogin> { RecordbookBridgeEntryPoint.from(androidContext()).barsLogin() }
    single<HttpClientEngine>(barsEngineQualifier) { RecordbookBridgeEntryPoint.from(androidContext()).barsEngine() }
}

/**
 * Koin to Hilt for the MyITMO and BARS data `recordbookModule` constructs, which Hilt-built code still takes:
 * `MarkTrackingRepositoryImpl` (the recordbook and the background BARS read), `MarksCheck` (the BARS switch) and the
 * BARS client itself, so Hilt never builds a second one. Unscoped on purpose: Koin owns the lifetime and returns its
 * single every time, so there is one `BarsClient` and one writer of `bars_tokens.enc` per process. The implementation
 * keys are read, so a debug fixture that overrides a domain type in Koin never reaches a Hilt singleton.
 * `ensureStarted`, because a worker can run before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object RecordbookBridge {

    @Provides
    fun recordbookRepository(@ApplicationContext context: Context): RecordbookRepository =
        KoinStarter.ensureStarted(context).get<RecordbookRepositoryImpl>()

    @Provides
    fun barsPreferenceRepository(@ApplicationContext context: Context): BarsPreferenceRepository =
        KoinStarter.ensureStarted(context).get<BarsPreferenceRepositoryImpl>()

    @Provides
    fun barsMarkSource(@ApplicationContext context: Context): BarsMarkSource =
        KoinStarter.ensureStarted(context).get<BarsMarkReader>()

    @Provides
    fun barsClient(@ApplicationContext context: Context): BarsClient = KoinStarter.ensureStarted(context).get()
}
