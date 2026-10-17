package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.core.notification.FcmWork
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import android.content.Context
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrCodeWidgetProvider
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages
import dev.alllexey.itmowidgets.feature.qr.work.QrWidgetWork
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.schedule.ui.widget.ScheduleWidgetProviders
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleWidgetWork
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetSnapshotStore
import dev.alllexey.itmowidgets.feature.sport.work.SportWidgetWork
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider

class AndroidSessionLifecycleEffects(
    private val context: Context,
    private val widgetSettings: WidgetSettingsPreferences,
    private val palettes: WidgetPaletteSource,
    private val scheduleWidgetStore: ScheduleWidgetSnapshotStore,
    private val sportWidgetStore: SportWidgetSnapshotStore,
    private val time: AcademicTimeProvider,
    private val qrWidgetStateStore: QrWidgetStateStore,
    private val qrWidgetImages: QrWidgetImages,
    private val notifier: AppNotifier,
    private val backgroundChecks: Set<BackgroundCheck>
) : SessionLifecycleEffects {

    override suspend fun prepareForSessionChange() {
        FcmWork.cancelMessages(context)
        notifier.clear()
        QrWidgetWork.cancelAll(context)
        ScheduleWidgetWork.cancelAll(context)
        SportWidgetWork.cancelAll(context)
        backgroundChecks.forEach { it.stopWork() }
    }

    override suspend fun onSignedIn() {
        backgroundChecks.forEach { it.syncWork() }
        if (QrCodeWidgetProvider.widgetIds(context).isNotEmpty()) {
            QrWidgetWork.enqueueUpdate(context, force = true)
        }
        if (ScheduleWidgetProviders.hasAnyWidget(context)) {
            ScheduleWidgetWork.ensurePeriodic(context)
            ScheduleWidgetWork.enqueueUpdate(context, force = true)
        }
        if (SportWidgetWork.ids(context).isNotEmpty()) {
            SportWidgetWork.ensurePeriodic(context)
            SportWidgetWork.enqueueUpdate(context, force = true)
        }
    }

    override suspend fun onSignedOut() {
        renderSignedOutScheduleWidgets()
        SportWidgetWork.render(context, sportWidgetStore.read(), palettes.current())
        renderSignedOutQrWidgets()
    }

    private suspend fun renderSignedOutScheduleWidgets() {
        val snapshot = ScheduleWidgetSnapshot.signedOut().withTextSizes(widgetSettings.getScheduleWidgetSettings())
        scheduleWidgetStore.write(snapshot)
        ScheduleWidgetProviders.render(context, snapshot, palettes.current(), time.now())
    }

    private suspend fun renderSignedOutQrWidgets() {
        val spoiler = qrWidgetImages.spoiler()
        QrCodeWidgetProvider.widgetIds(context).forEach { appWidgetId ->
            qrWidgetStateStore.setState(appWidgetId, QrWidgetState.HIDDEN)
            QrCodeWidgetProvider.render(context, appWidgetId, spoiler)
        }
    }
}
