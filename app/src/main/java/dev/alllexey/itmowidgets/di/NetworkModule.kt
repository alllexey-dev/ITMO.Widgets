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
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.util.OffsetDateTimeAdapter
import dev.alllexey.itmowidgets.core.utils.RuntimeTypeAdapterFactory
import dev.alllexey.itmowidgets.domain.model.sport.SportAutoSignEntry
import dev.alllexey.itmowidgets.domain.model.sport.SportAutoSignQueue
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportCommon
import dev.alllexey.itmowidgets.domain.model.sport.SportFreeSignEntry
import dev.alllexey.itmowidgets.domain.model.sport.SportFreeSignQueue
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.SportQueue
import dev.alllexey.itmowidgets.domain.model.sport.SportQueueEntry
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReason
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReasonTypeAdapter
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
        settings: AppSettingsStorage,
        @BackendBaseUrl baseUrl: String
    ): WidgetsClient {
        return WidgetsClient(
            myItmo = myItmo,
            settings = settings,
            baseUrl = baseUrl
        )
    }

    @Provides
    fun provideItmoWidgetsApi(client: WidgetsClient): ItmoWidgetsApi = client.api

    @Provides
    @Singleton
    fun provideGson(client: WidgetsClient): Gson {
        val commonFactory = RuntimeTypeAdapterFactory
            .of(SportCommon::class.java, "type", true)
            .registerSubtype(SportBooking::class.java, "booking")
            .registerSubtype(SportLesson::class.java, "lesson")
        val entryFactory = RuntimeTypeAdapterFactory
            .of(SportQueueEntry::class.java, "type", true)
            .registerSubtype(SportFreeSignEntry::class.java, "free")
            .registerSubtype(SportAutoSignEntry::class.java, "auto")
        val queueFactory = RuntimeTypeAdapterFactory
            .of(SportQueue::class.java, "type", true)
            .registerSubtype(SportFreeSignQueue::class.java, "free")
            .registerSubtype(SportAutoSignQueue::class.java, "auto")

        return client.gson.newBuilder()
            .registerTypeAdapter(UnavailableReason::class.java, UnavailableReasonTypeAdapter())
            .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeAdapter())
            .registerTypeAdapterFactory(commonFactory)
            .registerTypeAdapterFactory(entryFactory)
            .registerTypeAdapterFactory(queueFactory)
            .create()
    }
}
