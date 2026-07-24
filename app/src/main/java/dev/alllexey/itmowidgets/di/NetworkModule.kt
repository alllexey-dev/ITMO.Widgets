package dev.alllexey.itmowidgets.di

import api.myitmo.MyItmo
import api.myitmo.MyItmoApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.network.WidgetsClient
import dev.alllexey.itmowidgets.core.network.OffsetDateTimeAdapter
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import java.time.OffsetDateTime
import javax.inject.Qualifier
import javax.inject.Singleton

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
}
