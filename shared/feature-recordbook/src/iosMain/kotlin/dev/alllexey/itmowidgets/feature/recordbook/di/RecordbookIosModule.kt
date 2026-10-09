package dev.alllexey.itmowidgets.feature.recordbook.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.bars.auth.BarsLogin
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.darwinHttpEngine
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * The subject page's links are [UnofferedSubjectLinks] while iOS does not offer subject links
 * (`PlatformCapabilities.reviews`, IO-09f loads `resourcesModule` and removes the stand-in, since the graph refuses
 * an override); the teacher tones are `scheduleIosModule`'s stand-in until then.
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

    single<SubjectLinksRepository> { UnofferedSubjectLinks }
}

/**
 * The Koin parameters of `BarsLoginViewModel` on iOS, where a SwiftUI sheet owns it and no saved-state registry
 * exists: a fresh `SavedStateHandle`, so each sheet keeps one OAuth `state` for its lifetime
 * (`ScreenViewModelStore.resolve(type:parameters:)`).
 */
object BarsLoginParameters {
    fun fresh(): List<Any> = listOf(SavedStateHandle())
}

/**
 * The subject links while iOS does not offer them (App Review 1.2): no cache, no request, every answer
 * `CustomServicesDisabled` and no restrictions. The page hides the links (`linksEnabled`), and without a links answer a
 * sheet is offered only once connected; the flows still emit once, so the page's links-and-sheets stream starts.
 */
private object UnofferedSubjectLinks : SubjectLinksRepository {
    private val unoffered = AppResult.Failure(AppError.CustomServicesDisabled)

    override fun observe(scope: ResourceScope): Flow<SubjectLinksState> =
        flowOf(SubjectLinksState.Error(AppError.CustomServicesDisabled))

    override fun peek(scope: ResourceScope): SubjectLinksSnapshot? = null

    override suspend fun refresh(scope: ResourceScope): AppResult<Unit> = unoffered

    override suspend fun save(
        scope: ResourceScope,
        id: String,
        category: LinkCategory,
        url: String,
        title: String?,
        visibility: LinkVisibility,
        flowId: Long?,
    ): AppResult<SubjectLink> = unoffered

    override suspend fun delete(scope: ResourceScope, id: String): AppResult<Unit> = unoffered

    override suspend fun pin(scope: ResourceScope, id: String?): AppResult<Unit> = unoffered

    override suspend fun vote(scope: ResourceScope, id: String, value: Int): AppResult<Unit> = unoffered

    override suspend fun report(
        scope: ResourceScope,
        id: String,
        reason: ResourceReportReason,
        comment: String?,
    ): AppResult<Unit> = unoffered

    override fun observeRestrictions(): Flow<List<UserRestriction>> = flowOf(emptyList())

    override suspend fun refreshRestrictions(): AppResult<Unit> = unoffered
}
