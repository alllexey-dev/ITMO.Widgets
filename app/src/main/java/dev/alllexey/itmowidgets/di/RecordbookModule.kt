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
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.PeriodicCheckScheduler
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsSessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.home.MarksHomeCardSource
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.BarsMarksActivation
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.sheets.SheetScoresRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsBackgroundLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsCookieSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsHttp
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRenewal
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OwnerBoundBarsStorage
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.ItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.WebViewItmoIdCookies
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
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
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.CookieJar
import dev.alllexey.itmoapi.bars.BarsClient as LibraryBarsClient

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordbookModule {

    @Binds
    @Singleton
    abstract fun bindRecordbookRepository(
        impl: RecordbookRepositoryImpl
    ): RecordbookRepository

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
    @Singleton
    abstract fun bindBarsRepository(impl: BarsRecordbookRepositoryImpl): BarsRecordbookRepository

    @Binds
    @Singleton
    abstract fun bindBarsPreference(impl: BarsPreferenceRepositoryImpl): BarsPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindBarsSession(impl: BarsSessionRepositoryImpl): BarsSessionRepository

    @Binds
    abstract fun bindBarsSilentLogin(impl: BarsWebSilentLogin): BarsSilentLogin

    @Binds
    abstract fun bindItmoIdCookies(impl: WebViewItmoIdCookies): ItmoIdCookies

    @Binds
    abstract fun bindBarsBackgroundLogin(impl: BarsCookieSilentLogin): BarsBackgroundLogin

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
    abstract fun bindBarsMarkSource(impl: BarsMarkReader): BarsMarkSource

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

    @Binds
    @IntoSet
    abstract fun bindRecordbookCleaner(impl: BarsPreferenceRepositoryImpl): SessionDataCleaner

    @Binds
    @IntoSet
    abstract fun bindRecordbookCacheCleaner(impl: RecordbookRepositoryImpl): SessionDataCleaner

    @Binds
    @IntoSet
    abstract fun bindBarsCacheCleaner(impl: BarsRecordbookRepositoryImpl): SessionDataCleaner

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

        /** Library client with the app's encrypted, owner-bound session and its renewal; one per process. */
        @Provides
        @Singleton
        fun bars(
            @BarsHttp engine: HttpClientEngine,
            storage: OwnerBoundBarsStorage,
            renewal: BarsRenewal
        ): LibraryBarsClient = LibraryBarsClient(engine, storage = storage, codeSupplier = renewal)

        @Provides
        fun marksScheduler(@ApplicationContext context: Context): MarksScheduler =
            object : MarksScheduler, CheckScheduler by PeriodicCheckScheduler(context, MARKS_SPEC) {}

        /** The ITMO.ID sign-in of the `bars` client: URL, callback checks and the cookie replay. */
        @Provides
        @Singleton
        fun barsLogin(@BarsHttp engine: HttpClientEngine): BarsLogin = BarsLogin(engine)

        @Provides
        @Singleton
        fun barsTokens(@ApplicationContext context: Context, cipher: TokenCipher): BarsTokenStore =
            BarsTokenStore(File(context.noBackupFilesDir, "bars_tokens.enc"), cipher)
    }
}
