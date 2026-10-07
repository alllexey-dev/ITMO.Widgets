package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.core.diagnostics.AndroidAppLog
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.notification.FcmPayloadDispatcher
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking.QueueKind
import dev.alllexey.itmowidgets.feature.qr.presentation.QrTileController
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.social.data.push.FriendshipPushHandler
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Android-only bindings the workers, the widgets, the tile and the FCM service read (L17 KM-12a); Koin is their
 * only graph. Hilt's remaining readers (the diagnostics, the session effects) take them through `di/bridge`.
 */
val componentBindingsModule = module {
    singleOf(::AndroidAppLog) { bind<AppLog>() }

    // The widgets, the worker and the session effects share one store; the cleaner contribution is qualified.
    singleOf(::ScheduleWidgetSnapshotStoreImpl) { bind<ScheduleWidgetSnapshotStore>() }
    single<SessionDataCleaner>(named("schedule-widget")) { get<ScheduleWidgetSnapshotStoreImpl>() }

    factoryOf(::QrTileController)

    // An open set: each handler carries its own qualifier, the dispatcher reads them all.
    singleOf(::FriendshipPushHandler) { named("friendship"); bind<FcmPayloadHandler>() }
    single<FcmPayloadHandler>(named("sport-free")) { SportSignPushHandler(QueueKind.FREE, get(), get()) }
    single<FcmPayloadHandler>(named("sport-auto")) { SportSignPushHandler(QueueKind.AUTO, get(), get()) }
    single<FcmPayloadDispatcher> { FcmPayloadDispatcher(getAll(), get()) }
}
