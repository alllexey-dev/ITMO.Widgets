package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.home.MarksHomeCardSource
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.BarsMarksActivation
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsHttp
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.work.AndroidMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.work.MARKS_SPEC
import dev.alllexey.itmowidgets.feature.recordbook.work.WorkManagerBarsSessionProbe
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.CookieJar

/**
 * The recordbook's Android bindings: the BARS engine and its ITMO.ID sign-in, the WebView sign-in and cookies, the marks
 * worker's scheduler and
 * notifier, and the recordbook data still built by Hilt (mark tracking, sheet scores, subject bindings; KM-11b2 moves
 * them). The MyITMO and BARS data is Koin's (`recordbookModule`); `di/bridge/RecordbookBridge.kt` connects both graphs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordbookModule {

    @Binds
    @Singleton
    abstract fun bindSubjectBindingStore(
        impl: DataStoreSubjectBindingStore
    ): SubjectBindingStore

    @Binds
    @IntoSet
    abstract fun bindSubjectBindingCleaner(
        impl: DataStoreSubjectBindingStore
    ): SessionDataCleaner

    @Binds
    abstract fun bindBarsSilentLogin(impl: BarsWebSilentLogin): BarsSilentLogin

    @Binds
    abstract fun bindItmoIdCookies(impl: WebViewItmoIdCookies): ItmoIdCookies

    @Binds
    abstract fun bindBarsSessionProbe(impl: WorkManagerBarsSessionProbe): BarsSessionProbe

    @Binds
    @Singleton
    abstract fun bindMarkTrackingRepository(impl: MarkTrackingRepositoryImpl): MarkTrackingRepository

    @Binds
    @IntoSet
    abstract fun bindMarkTrackingCleaner(impl: MarkTrackingRepositoryImpl): SessionDataCleaner

    @Binds
    @Singleton
    abstract fun bindSheetScoresRepository(impl: SheetScoresRepositoryImpl): SheetScoresRepository

    @Binds
    @IntoSet
    abstract fun bindSheetScoresCleaner(impl: SheetScoresRepositoryImpl): SessionDataCleaner

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMarksHomeCards(impl: MarksHomeCardSource): HomeCardSource

    @Binds
    abstract fun bindMarksNotifier(impl: AndroidMarksNotifier): MarksNotifier

    @Binds
    abstract fun bindBarsSessionListener(impl: BarsMarksActivation): BarsSessionListener

    @Binds
    @Singleton
    abstract fun bindMarkTracking(impl: DefaultMarkTracking): MarkTracking

    @Binds
    @IntoSet
    abstract fun bindMarksBackgroundCheck(impl: DefaultMarkTracking): BackgroundCheck

    companion object {
        /**
         * The BARS engine, apart from MyITMO's: the ADR 0012 policy of MyItmoApi's `defaultEngine()` (no cookie jar,
         * cache or redirects) with the BARS timeouts. Neither client closes it; it lives as long as the process.
         */
        @Provides
        @Singleton
        @BarsHttp
        fun barsEngine(): HttpClientEngine = OkHttp.create {
            config {
                followRedirects(false)
                followSslRedirects(false)
                cookieJar(CookieJar.NO_COOKIES)
                cache(null)
                connectTimeout(20, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }

        @Provides
        fun marksScheduler(@ApplicationContext context: Context): MarksScheduler =
            object : MarksScheduler, CheckScheduler by PeriodicCheckScheduler(context, MARKS_SPEC) {}

        /**
         * The ITMO.ID sign-in of the `bars` client: URL, callback checks and the cookie replay. Built here over the
         * engine; `recordbookModule` takes this one instance through `RecordbookBridge`.
         */
        @Provides
        @Singleton
        fun barsLogin(@BarsHttp engine: HttpClientEngine): BarsLogin = BarsLogin(engine)
    }
}
