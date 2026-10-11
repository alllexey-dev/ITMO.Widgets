package dev.alllexey.itmowidgets.di

import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.di.barsEngineQualifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.work.AndroidMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.work.MARKS_SPEC
import dev.alllexey.itmowidgets.feature.recordbook.work.WorkManagerBarsSessionProbe
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The recordbook's Android side: the BARS engine and its ITMO.ID sign-in, the WebView sign-in and cookies, the BARS
 * session probe, the marks worker's scheduler and notifier, and the mark check's place among the background checks.
 * All recordbook data is `recordbookModule`'s.
 */
val androidRecordbookModule = module {
    // The BARS engine, apart from MyITMO's: the policy of MyItmoApi's `defaultEngine()` (no cookie jar, cache or
    // redirects) with the BARS timeouts. Neither client closes it; it lives as long as the process.
    single<HttpClientEngine>(barsEngineQualifier) {
        OkHttp.create {
            config {
                followRedirects(false)
                followSslRedirects(false)
                cookieJar(CookieJar.NO_COOKIES)
                cache(null)
                connectTimeout(20, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
            }
        }
    }
    // The ITMO.ID sign-in of the `bars` client: URL, callback checks and the cookie replay.
    single { BarsLogin(get<HttpClientEngine>(barsEngineQualifier)) }
    singleOf(::BarsWebSilentLogin) { bind<BarsSilentLogin>() }
    singleOf(::WebViewItmoIdCookies) { bind<ItmoIdCookies>() }
    singleOf(::WorkManagerBarsSessionProbe) { bind<BarsSessionProbe>() }
    singleOf(::AndroidMarksNotifier) { bind<MarksNotifier>() }
    single<MarksScheduler> {
        object : MarksScheduler, CheckScheduler by PeriodicCheckScheduler(androidContext(), MARKS_SPEC) {}
    }
    // The implementation key, so a debug fixture that overrides MarkTracking never reaches the background checks.
    single<BackgroundCheck>(named("marks")) { get<DefaultMarkTracking>() }
}
