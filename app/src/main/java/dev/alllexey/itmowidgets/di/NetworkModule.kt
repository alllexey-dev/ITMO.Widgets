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
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.network.BackendClientFactory
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.network.WidgetsClient
import dev.alllexey.itmowidgets.core.network.OffsetDateTimeAdapter
import dev.alllexey.itmowidgets.core.network.PublicWebClient
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import io.ktor.client.engine.HttpClientEngine
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
     * The one Ktor engine of MyItmoApi 2.x and Core 2.0: OkHttp without cookies, cache or redirects. Neither client
     * closes it; it lives as long as the process.
     */
    @Provides
    @Singleton
    fun provideHttpClientEngine(): HttpClientEngine = defaultEngine()

    /** MyItmoApi 2.x. It shares [TokenStorage] with the 1.x [MyItmo] above, which serves the areas not moved yet. */
    @Provides
    @Singleton
    fun provideMyItmoClient(storage: TokenStorage, engine: HttpClientEngine, clock: Clock): MyItmoClient =
        MyItmoClientFactory.create(storage = storage, engine = engine, clock = clock)

    @Provides
    @BackendBaseUrl
    fun provideBackendBaseUrl(): String = BuildConfig.WIDGETS_BASE_URL

    /**
     * Core 2.0 beside the 1.x [WidgetsClient] below, which serves the areas not moved yet (until KM-10i). Its token
     * comes from the 2.x client's `tokens`, the only refresher and writer of the session.
     */
    @Provides
    @Singleton
    fun provideBackendClient(
        @BackendBaseUrl baseUrl: String,
        myItmo: MyItmoClient,
        engine: HttpClientEngine
    ): BackendClient = BackendClientFactory.create(baseUrl = baseUrl, tokens = myItmo.tokens, engine = engine)

    @Provides
    fun provideUsersApi(client: BackendClient): UsersApi = client.users

    @Provides
    fun provideFriendsApi(client: BackendClient): FriendsApi = client.friends

    @Provides
    fun provideBackendSportApi(client: BackendClient): SportApi = client.sport

    @Provides
    fun provideSubjectLinksApi(client: BackendClient): SubjectLinksApi = client.links

    @Provides
    fun provideTeacherReviewsApi(client: BackendClient): TeacherReviewsApi = client.reviews

    @Provides
    fun provideDeviceApi(client: BackendClient): DeviceApi = client.device

    @Provides
    fun provideAppApi(client: BackendClient): AppApi = client.app

    @Provides
    fun provideBackendScheduleApi(client: BackendClient): ScheduleApi = client.schedule

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
