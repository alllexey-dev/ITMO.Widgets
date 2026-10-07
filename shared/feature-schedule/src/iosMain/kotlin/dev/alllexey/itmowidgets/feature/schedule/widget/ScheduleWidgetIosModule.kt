package dev.alllexey.itmowidgets.feature.schedule.widget

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetDataProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * The schedule widgets' App Group timeline (IO-10b): [ScheduleTimelineWriter] starts with the graph and writes on the
 * main queue (WidgetKit reloads are asked there) on every trigger for the life of the app process; IO-14's background
 * runner calls its `publish` too. It is also the iOS `ScheduleWidgetRefreshRequester`, which sport asks after a
 * booking.
 *
 * The provider and the cached schedule are resolved at each write from the schedule data graph (`scheduleDataModule`,
 * `scheduleModule`, the pending sport rows); until that graph is loaded a write only logs why it could not load.
 */
val scheduleWidgetIosModule = module {
    single(createdAtStart = true) {
        val scope = this
        ScheduleTimelineWriter(
            source = { until -> scope.get<ScheduleWidgetDataProvider>().loadTimeline(until) },
            writer = AppGroupSnapshotWriter(get(), get(), ScheduleTimelineWriter.JSON),
            timeProvider = get(),
            clock = get(),
            log = get(),
        ).also { writer ->
            writer.launchIn(CoroutineScope(SupervisorJob() + get<AppDispatchers>().main), triggers())
        }
    } bind ScheduleWidgetRefreshRequester::class
}

private fun Scope.triggers(): ScheduleTimelineTriggers {
    val scope = this
    return ScheduleTimelineTriggers(
        session = get<SessionRepository>().state.mapNotNull(::sessionKind),
        widgetSettings = get<WidgetSettingsPreferences>().observeScheduleWidgetSettings(),
        foregrounds = applicationForegrounds(),
        cachedSchedule = flow {
            // The widgets' days as they are when the app starts; a later day is covered by the foreground write.
            val today = scope.get<AcademicTimeProvider>().localNow().date
            emitAll(
                scope.get<ScheduleRepository>()
                    .observeScheduleForRange(userIsu = null, startDate = today, endDate = today.plus(2, DateTimeUnit.DAY))
            )
        },
    )
}

/** Only settled sessions count: the writer then reads the tokens the provider reads. */
private fun sessionKind(state: SessionState): Any? = when (state) {
    is SessionState.SignedIn -> if (state.demo) "demo" else "signed-in"
    SessionState.SignedOut, SessionState.ReauthenticationRequired -> "signed-out"
    SessionState.Initializing, SessionState.SigningOut -> null
}

/** Every return of the app from the background. */
private fun applicationForegrounds(): Flow<Unit> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val observer = center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) {
        trySend(Unit)
    }
    awaitClose { center.removeObserver(observer) }
}
