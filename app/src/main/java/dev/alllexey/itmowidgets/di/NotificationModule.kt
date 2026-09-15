package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import dev.alllexey.itmowidgets.app.AndroidAppNotifier
import dev.alllexey.itmowidgets.core.notification.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds @Singleton abstract fun tokenSync(impl: DefaultFcmTokenSync): FcmTokenSync
    @Binds abstract fun tokenProvider(impl: DefaultFirebaseTokenProvider): FirebaseTokenProvider
    @Binds @Singleton abstract fun notifier(impl: AndroidAppNotifier): AppNotifier
    @Multibinds abstract fun handlers(): Set<FcmPayloadHandler>
}
