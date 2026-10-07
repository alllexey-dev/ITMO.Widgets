package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsHttp
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.work.AndroidMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.work.MARKS_SPEC
import dev.alllexey.itmowidgets.feature.recordbook.work.WorkManagerBarsSessionProbe
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.CookieJar

/**
 * The recordbook's Android bindings only: the BARS engine and its ITMO.ID sign-in, the WebView sign-in and cookies, the
 * BARS session probe, and the marks worker's scheduler and notifier. All recordbook data is Koin's
 * (`recordbookModule`); `di/bridge/RecordbookBridge.kt` connects both graphs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordbookModule {

    @Binds
    abstract fun bindBarsSilentLogin(impl: BarsWebSilentLogin): BarsSilentLogin

    @Binds
    abstract fun bindItmoIdCookies(impl: WebViewItmoIdCookies): ItmoIdCookies

    @Binds
    abstract fun bindBarsSessionProbe(impl: WorkManagerBarsSessionProbe): BarsSessionProbe

    @Binds
    abstract fun bindMarksNotifier(impl: AndroidMarksNotifier): MarksNotifier

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
