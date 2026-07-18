package dev.alllexey.itmowidgets.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import api.myitmo.MyItmo
import api.myitmo.MyItmoApi
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsImpl
import dev.alllexey.itmowidgets.core.debug.DefaultSportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SharedPreferencesSportScoreOverrideStore
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideStore
import dev.alllexey.itmowidgets.core.network.WidgetsClient
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.MyItmoStorage
import dev.alllexey.itmowidgets.core.time.AcademicClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.SharedPreferencesAcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.util.OffsetDateTimeAdapter
import dev.alllexey.itmowidgets.core.utils.RuntimeTypeAdapterFactory
import dev.alllexey.itmowidgets.domain.model.sport.SportBooking
import dev.alllexey.itmowidgets.domain.model.sport.SportCommon
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReason
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReasonTypeAdapter
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    @AcademicClock
    fun provideAcademicClock(): Clock = Clock.system(ZoneId.of("Europe/Moscow"))

    @Provides
    @Singleton
    fun provideAcademicTimeOverrideStore(
        preferences: SharedPreferences
    ): AcademicTimeOverrideStore = SharedPreferencesAcademicTimeOverrideStore(preferences)

    @Provides
    @Singleton
    fun provideDefaultAcademicTimeProvider(
        @AcademicClock clock: Clock,
        overrideStore: AcademicTimeOverrideStore
    ): DefaultAcademicTimeProvider = DefaultAcademicTimeProvider(clock, overrideStore)

    @Provides
    fun provideAcademicTimeProvider(
        provider: DefaultAcademicTimeProvider
    ): AcademicTimeProvider = provider

    @Provides
    fun provideAcademicTimeOverrideController(
        provider: DefaultAcademicTimeProvider
    ): AcademicTimeOverrideController = provider

    @Provides
    @Singleton
    fun provideSportScoreOverrideStore(
        preferences: SharedPreferences
    ): SportScoreOverrideStore = SharedPreferencesSportScoreOverrideStore(preferences)

    @Provides
    @Singleton
    fun provideDefaultSportScoreOverrideProvider(
        overrideStore: SportScoreOverrideStore
    ): DefaultSportScoreOverrideProvider = DefaultSportScoreOverrideProvider(overrideStore)

    @Provides
    fun provideSportScoreOverrideProvider(
        provider: DefaultSportScoreOverrideProvider
    ): SportScoreOverrideProvider = provider

    @Provides
    fun provideSportScoreOverrideController(
        provider: DefaultSportScoreOverrideProvider
    ): SportScoreOverrideController = provider

    @Provides
    @Singleton
    fun provideMyItmo(storage: MyItmoStorage): MyItmo {
        return MyItmo().apply { this.storage = storage }
    }

    @Provides
    fun provideMyItmoApi(myItmo: MyItmo): MyItmoApi = myItmo.api

    @Provides
    @Singleton
    fun provideWidgetsClient(
        myItmo: MyItmo,
        settings: AppSettingsStorage
    ): WidgetsClient {
        return WidgetsClient(
            myItmo = myItmo,
            settings = settings,
            baseUrl = ItmoWidgetsImpl.DEV_BASE_URL
        )
    }

    @Provides
    fun provideItmoWidgetsApi(client: WidgetsClient): ItmoWidgetsApi = client.api

    @Provides
    @Singleton
    fun provideGson(client: WidgetsClient): Gson {
        val factory = RuntimeTypeAdapterFactory
            .of(SportCommon::class.java, "type", true)
            .registerSubtype(SportBooking::class.java, "booking")
            .registerSubtype(SportLesson::class.java, "lesson")

        return client.gson.newBuilder()
            .registerTypeAdapter(UnavailableReason::class.java, UnavailableReasonTypeAdapter())
            .registerTypeAdapter(OffsetDateTime::class.java, OffsetDateTimeAdapter())
            .registerTypeAdapterFactory(factory)
            .create()
    }
}
