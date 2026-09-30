package dev.alllexey.itmowidgets.di

import android.content.Context
import api.bars.Bars
import api.bars.utils.BarsAuthHelper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.debug.BarsSessionProbe
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsSessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.home.MarksHomeCardSource
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.BarsMarksActivation
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.DefaultMarkTracking
import dev.alllexey.itmowidgets.feature.recordbook.data.marks.MarkTrackingRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsBackgroundLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsCookieSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkReader
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsMarkSource
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
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
import dev.alllexey.itmowidgets.feature.recordbook.work.AndroidMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.work.WorkManagerBarsSessionProbe
import dev.alllexey.itmowidgets.feature.recordbook.work.WorkManagerMarksScheduler
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

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
    @IntoSet
    @Singleton
    abstract fun bindMarksHomeCards(impl: MarksHomeCardSource): HomeCardSource

    @Binds
    abstract fun bindBarsMarkSource(impl: BarsMarkReader): BarsMarkSource

    @Binds
    abstract fun bindMarksScheduler(impl: WorkManagerMarksScheduler): MarksScheduler

    @Binds
    abstract fun bindMarksNotifier(impl: AndroidMarksNotifier): MarksNotifier

    @Binds
    abstract fun bindBarsSessionListener(impl: BarsMarksActivation): BarsSessionListener

    @Binds
    @Singleton
    abstract fun bindMarkTracking(impl: DefaultMarkTracking): MarkTracking

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
        /** Library client with the app's encrypted, owner-bound session; no shared cookie jar with MyITMO. */
        @Provides
        @Singleton
        fun bars(storage: OwnerBoundBarsStorage): Bars = Bars().apply {
            this.storage = storage
            okHttpClient = okHttpClient.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }

        @Provides
        fun barsAuthHelper(bars: Bars): BarsAuthHelper = bars.authHelper

        @Provides
        @Singleton
        fun barsTokens(@ApplicationContext context: Context, cipher: TokenCipher): BarsTokenStore =
            BarsTokenStore(File(context.noBackupFilesDir, "bars_tokens.enc"), cipher)
    }
}
