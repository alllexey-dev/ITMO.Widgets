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
import dev.alllexey.itmowidgets.feature.sport.data.push.SportAgentBookedPushHandler
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushHandler
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.connections.data.push.DeviceConnectedPushHandler
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Android-only bindings the workers, the widgets, the tile and the FCM service read.
 */
val componentBindingsModule = module {
    singleOf(::AndroidAppLog) { bind<AppLog>() }

    // The widgets, the worker and the session effects share one store; the cleaner contribution is qualified.
    singleOf(::ScheduleWidgetSnapshotStoreImpl) { bind<ScheduleWidgetSnapshotStore>() }
    single<SessionDataCleaner>(named("schedule-widget")) { get<ScheduleWidgetSnapshotStoreImpl>() }
    singleOf(::SportWidgetSnapshotStoreImpl) { bind<SportWidgetSnapshotStore>() }
    single<SessionDataCleaner>(named("sport-widget")) { get<SportWidgetSnapshotStoreImpl>() }

    factoryOf(::QrTileController)

    // An open set: each handler carries its own qualifier, the dispatcher reads them all.
    singleOf(::FriendshipPushHandler) { named("friendship"); bind<FcmPayloadHandler>() }
    single<FcmPayloadHandler>(named("sport-free")) { SportSignPushHandler(QueueKind.FREE, get()) }
    single<FcmPayloadHandler>(named("sport-auto")) { SportSignPushHandler(QueueKind.AUTO, get()) }
    singleOf(::SportAgentBookedPushHandler) { named("sport-agent"); bind<FcmPayloadHandler>() }
    singleOf(::DeviceConnectedPushHandler) { named("device-connected"); bind<FcmPayloadHandler>() }
    single<FcmPayloadDispatcher> { FcmPayloadDispatcher(getAll(), get(), get(), get()) }
}
