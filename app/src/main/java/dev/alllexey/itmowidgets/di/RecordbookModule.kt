package dev.alllexey.itmowidgets.di

import android.content.Context
import com.google.gson.Gson
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
import dev.alllexey.itmowidgets.feature.recordbook.data.RecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsApi
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsRecordbookRepositoryImpl
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsWebSilentLogin
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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

    companion object {
        @Provides
        @Singleton
        fun barsApi(): BarsApi = Retrofit.Builder()
            .baseUrl("https://bars.itmo.ru/backend/rest/")
            .client(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build().create(BarsApi::class.java)

        @Provides
        @Singleton
        fun barsTokens(@ApplicationContext context: Context, cipher: TokenCipher): BarsTokenStore =
            BarsTokenStore(File(context.noBackupFilesDir, "bars_tokens.enc"), cipher)
    }
}
