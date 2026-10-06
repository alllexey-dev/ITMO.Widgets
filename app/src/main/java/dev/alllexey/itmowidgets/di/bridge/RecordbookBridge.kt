package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsHttp
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksCheck
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import io.ktor.client.engine.HttpClientEngine
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the Android side of the recordbook that `recordbookModule` takes: the BARS engine and its ITMO.ID
 * sign-in `BarsLogin`, the WebView silent login, the ITMO.ID cookies, and the marks worker's scheduler and
 * notifications. Each one is the instance Hilt hands out, so Koin and Hilt readers share it (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecordbookBridgeEntryPoint {
    fun barsSilentLogin(): BarsSilentLogin
    fun itmoIdCookies(): ItmoIdCookies
    fun barsLogin(): BarsLogin
    fun marksScheduler(): MarksScheduler
    fun marksNotifier(): MarksNotifier

    @BarsHttp
    fun barsEngine(): HttpClientEngine

    companion object {
        fun from(context: Context): RecordbookBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, RecordbookBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val recordbookBridgeModule = module {
    // Unscoped in Hilt and stateless: Koin keeps the first instance it gets, which the one BarsRenewal holds.
    single<BarsSilentLogin> { RecordbookBridgeEntryPoint.from(androidContext()).barsSilentLogin() }
    single<ItmoIdCookies> { RecordbookBridgeEntryPoint.from(androidContext()).itmoIdCookies() }
    single<BarsLogin> { RecordbookBridgeEntryPoint.from(androidContext()).barsLogin() }
    single<HttpClientEngine>(barsEngineQualifier) { RecordbookBridgeEntryPoint.from(androidContext()).barsEngine() }
    // Unscoped in Hilt and stateless (WorkManager and the notification manager hold the state).
    single<MarksScheduler> { RecordbookBridgeEntryPoint.from(androidContext()).marksScheduler() }
    single<MarksNotifier> { RecordbookBridgeEntryPoint.from(androidContext()).marksNotifier() }
}

/**
 * Koin to Hilt for the recordbook data `recordbookModule` constructs that Hilt-built code still takes: the marks check
 * of `MarksWorker` (through `MarksEntryPoint`), the switches of [MarkTracking] (debug tools, the marks test entry
 * point) and the same instance as one of the Application's background checks. Unscoped on purpose: Koin owns the
 * lifetime and returns its single every time. The implementation key is read, so a debug fixture that overrides a
 * domain type in Koin never reaches Hilt. `ensureStarted`, because a worker can run before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object RecordbookBridge {

    @Provides
    fun marksCheck(@ApplicationContext context: Context): MarksCheck = KoinStarter.ensureStarted(context).get()

    @Provides
    fun markTracking(@ApplicationContext context: Context): MarkTracking =
        KoinStarter.ensureStarted(context).get<DefaultMarkTracking>()

    @Provides
    @IntoSet
    fun marksBackgroundCheck(@ApplicationContext context: Context): BackgroundCheck =
        KoinStarter.ensureStarted(context).get<DefaultMarkTracking>()
}
