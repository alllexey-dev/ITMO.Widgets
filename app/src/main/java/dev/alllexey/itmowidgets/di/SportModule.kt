package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking.QueueKind
import dev.alllexey.itmowidgets.feature.sport.data.debug.DefaultSportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushHandler
import javax.inject.Singleton

/**
 * What sport keeps on Hilt: the FCM handlers of the free and auto queue pushes, which post the Android notification
 * around the shared booking decision, and the debug lesson templates. The sport data is Koin's (`sportModule`);
 * `SportBridge` carries the types between the graphs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SportModule {

    @Binds
    @Singleton
    abstract fun bindSportLessonTemplateProvider(
        impl: DefaultSportLessonTemplateProvider
    ): SportLessonTemplateProvider

    companion object {
        @Provides @IntoSet
        fun freeSignHandler(factory: SportSignPushHandler.Factory): FcmPayloadHandler = factory.create(QueueKind.FREE)

        @Provides @IntoSet
        fun autoSignHandler(factory: SportSignPushHandler.Factory): FcmPayloadHandler = factory.create(QueueKind.AUTO)
    }
}
