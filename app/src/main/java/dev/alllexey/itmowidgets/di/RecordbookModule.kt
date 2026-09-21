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
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.TokenCipher
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsPreferenceRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.BarsSessionRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.DataStoreSubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.OwnerBoundBarsStorage
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
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
    @IntoSet
    abstract fun bindRecordbookCleaner(impl: BarsPreferenceRepositoryImpl): SessionDataCleaner

    @Binds
    @IntoSet
    abstract fun bindRecordbookCacheCleaner(impl: RecordbookRepositoryImpl): SessionDataCleaner

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
