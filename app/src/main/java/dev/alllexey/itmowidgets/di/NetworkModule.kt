package dev.alllexey.itmowidgets.di

import api.myitmo.MyItmo
import api.myitmo.MyItmoApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmoapi.core.defaultEngine
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.network.WidgetsClient
import dev.alllexey.itmowidgets.core.network.OffsetDateTimeAdapter
import dev.alllexey.itmowidgets.core.network.PublicWebClient
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import okhttp3.CookieJar
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendBaseUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMyItmo(storage: MyItmoStorage): MyItmo {
        return MyItmo().apply { this.storage = storage }
    }

    @Provides
    fun provideMyItmoApi(myItmo: MyItmo): MyItmoApi = myItmo.api

    /**
     * MyItmoApi 2.x over Ktor's OkHttp engine without cookies, cache or redirects. It shares [TokenStorage] with the
     * 1.x [MyItmo] above, which serves the areas not moved yet.
     */
    @Provides
    @Singleton
    fun provideMyItmoClient(storage: TokenStorage, clock: Clock): MyItmoClient =
        MyItmoClientFactory.create(storage = storage, engine = defaultEngine(), clock = clock)

    @Provides
    @BackendBaseUrl
    fun provideBackendBaseUrl(): String = BuildConfig.WIDGETS_BASE_URL

    @Provides
    @Singleton
    fun provideWidgetsClient(
        myItmo: MyItmo,
        @BackendBaseUrl baseUrl: String
    ): WidgetsClient {
        return WidgetsClient(
            myItmo = myItmo,
            baseUrl = baseUrl
        )
    }

    @Provides
    fun provideItmoWidgetsApi(client: WidgetsClient): ItmoWidgetsApi = client.api

    @Provides
    @Singleton
    fun provideGson(client: WidgetsClient): Gson {
        return client.gson.newBuilder()
            .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeAdapter())
            .create()
    }

    /** Public Google Sheets: no cookies, no HTTPS-to-HTTP redirects. */
    @Provides
    @Singleton
    @PublicWebClient
    fun providePublicWebClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
}
