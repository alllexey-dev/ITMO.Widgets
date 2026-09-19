package dev.alllexey.itmowidgets.app

import android.appwidget.AppWidgetManager
import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetWork
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetRenderer
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import javax.inject.Inject

class AndroidSessionLifecycleEffects @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: AppSettingsStorage,
    private val scheduleWidgetStore: ScheduleWidgetSnapshotStore,
    private val qrWidgetStateStore: QrWidgetStateStore,
    private val qrWidgetImages: QrWidgetImages,
    private val notifier: AppNotifier
) : SessionLifecycleEffects {

    override suspend fun prepareForSessionChange() {
        FcmWork.cancelMessages(context)
        notifier.clear()
        QrWidgetWork.cancelAll(context)
        ScheduleWidgetWork.cancelAll(context)
    }

    override suspend fun onSignedIn() {
        if (QrCodeWidgetProvider.widgetIds(context).isNotEmpty()) {
            QrWidgetWork.enqueueUpdate(context, force = true)
        }
        if (ScheduleWidgetProviders.hasAnyWidget(context)) {
            ScheduleWidgetWork.ensurePeriodic(context)
            ScheduleWidgetWork.enqueueUpdate(context, force = true)
        }
    }

    override suspend fun onSignedOut() {
        renderSignedOutScheduleWidgets()
        renderSignedOutQrWidgets()
    }

    private suspend fun renderSignedOutScheduleWidgets() {
        val snapshot = ScheduleWidgetSnapshot.signedOut(
            singleLessonStyle = settings.getSingleLessonWidgetStyle(),
            lessonListStyle = settings.getLessonListWidgetStyle()
        ).withTextSizes(settings.getScheduleWidgetSettings())
        scheduleWidgetStore.write(snapshot)

        val manager = AppWidgetManager.getInstance(context)
        ScheduleWidgetProviders.singleLessonIds(context).forEach { appWidgetId ->
            ScheduleWidgetRenderer.renderSingle(
                context = context,
                appWidgetManager = manager,
                appWidgetId = appWidgetId,
                snapshot = snapshot
            )
        }
        ScheduleWidgetRenderer.notifyListChanged(
            manager,
            ScheduleWidgetProviders.dayScheduleIds(context)
        )
    }

    private suspend fun renderSignedOutQrWidgets() {
        val spoiler = qrWidgetImages.spoiler()
        QrCodeWidgetProvider.widgetIds(context).forEach { appWidgetId ->
            qrWidgetStateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
            QrCodeWidgetProvider.render(context, appWidgetId, spoiler)
        }
    }
}
