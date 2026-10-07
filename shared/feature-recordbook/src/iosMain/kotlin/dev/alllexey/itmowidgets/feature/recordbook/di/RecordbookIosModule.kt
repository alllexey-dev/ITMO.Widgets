package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.darwinHttpEngine
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionCheck
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebHost
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookieExport
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookieExportOnSignIn
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.KeychainItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewBarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * The iOS ports of [recordbookModule], what `:app`'s `RecordbookModule` and `RecordbookBridge` give Android; load it
 * with that module. BARS (IO-09d1): its own Darwin engine without cookies, cache or redirects ([barsEngineQualifier],
 * never the MyITMO one), the ITMO.ID sign-in of the `bars` client over it, the ITMO.ID cookies in the Keychain, the
 * hidden-WebView renewal on [host], and the cookie copy after an interactive ITMO.ID sign-in. `recordbookModule`
 * keeps the one `BarsClient`, `BarsTokenStore` (`bars_tokens.enc`, a Keychain item through `SecureStore`) and
 * `OwnerBoundBarsStorage` of the process.
 *
 * Until the mark check runs on iOS (IO-09d3, IO-14) nothing is scheduled or notified: a BARS answer still turns
 * "Оценки БАРС" on through `BarsMarksActivation`, which needs both ports.
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

    // IO-09d3 binds the background mark check's scheduler, IO-14 the UNUserNotificationCenter notifier in the core
    // module; each card removes its line here.
    single<MarksScheduler> { UnscheduledMarks }
    single<AppNotifier> { UnpostedNotifications }
}

/**
 * The Koin parameters of `BarsLoginViewModel` on iOS, where a SwiftUI sheet owns it and no saved-state registry
 * exists: a fresh `SavedStateHandle`, so each sheet keeps one OAuth `state` for its lifetime
 * (`ScreenViewModelStore.resolve(type:parameters:)`).
 */
object BarsLoginParameters {
    fun fresh(): List<Any> = listOf(SavedStateHandle())
}

private object UnscheduledMarks : MarksScheduler {
    override fun ensurePeriodic() = Unit

    override fun runOnce() = Unit

    override fun cancel() = Unit
}

private object UnpostedNotifications : AppNotifier {
    override fun show(notification: AppNotification) = Unit

    override fun cancel(channel: String, id: Int) = Unit

    override fun clear() = Unit
}
