package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.darwinHttpEngine
import dev.alllexey.itmowidgets.core.work.AppRefreshScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionCheck
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebHost
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookieExport
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookieExportOnSignIn
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.KeychainItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewBarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.IosMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarksRefresh
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MorningMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.RefreshTaskMarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * The iOS ports of [recordbookModule], what `:app`'s `RecordbookModule` and `RecordbookBridge` give Android; load it
 * with that module. BARS (IO-09d1): its own Darwin engine without cookies, cache or redirects ([barsEngineQualifier],
 * never the MyITMO one), the ITMO.ID sign-in of the `bars` client over it, the ITMO.ID cookies in the Keychain, the
 * hidden-WebView renewal on [host], and the cookie copy after an interactive ITMO.ID sign-in. `recordbookModule`
 * keeps the one `BarsClient`, `BarsTokenStore` (`bars_tokens.enc`, a Keychain item through `SecureStore`) and
 * `OwnerBoundBarsStorage` of the process.
 *
 * The background mark check (IO-09d3): its notifier over `iosBackgroundModule`'s `IosAppNotifier` (IO-14), the app
 * refresh task as its scheduler, and the runner's step under [RefreshStepKeys.MARKS] with Android's three hours.
 * BARS renews there through the ITMO.ID cookies only (`BarsCookieSilentLogin`), never the hidden WebView.
 *
 * The subject page's links come from `resourcesModule` and the teacher tones from `reviewsModule` (IO-09f).
 */
fun recordbookIosModule(host: BarsWebHost): Module = module {
    single<HttpClientEngine>(barsEngineQualifier) { darwinHttpEngine() }
    single { BarsLogin(get(barsEngineQualifier)) }
    singleOf(::KeychainItmoIdCookies) { bind<ItmoIdCookies>() }
    single { ItmoIdCookieExport(host, get(), get(), get()) }
    single<BarsSilentLogin> { WebViewBarsSilentLogin(host, get(), get(), get()) }
    single(createdAtStart = true) {
        ItmoIdCookieExportOnSignIn(get(), get()).also { export ->
            export.launchIn(CoroutineScope(SupervisorJob() + get<AppDispatchers>().main))
        }
    }
    factoryOf(::BarsSessionCheck)

    single { IosMarksNotifier(get()) } binds arrayOf(MarksNotifier::class, MorningMarksNotifier::class)
    single<MarksScheduler> { RefreshTaskMarksScheduler(get<AppRefreshScheduler>(), get()) }
    single { MarksRefresh(get(), get(), get(), get(), get(), get()) }
    single(named(RefreshStepKeys.MARKS)) {
        val refresh = get<MarksRefresh>()
        RefreshStep(RefreshStepKeys.MARKS, RefreshStepKeys.MARKS_PERIOD) { refresh.run() }
    }
}

/**
 * The Koin parameters of `BarsLoginViewModel` on iOS, where a SwiftUI sheet owns it and no saved-state registry
 * exists: a fresh `SavedStateHandle`, so each sheet keeps one OAuth `state` for its lifetime
 * (`ScreenViewModelStore.resolve(type:parameters:)`).
 */
object BarsLoginParameters {
    fun fresh(): List<Any> = listOf(SavedStateHandle())
}
