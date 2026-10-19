package dev.alllexey.itmowidgets.feature.sport.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.storage.AppGroupSnapshotWriter
import dev.alllexey.itmowidgets.core.storage.AppGroupWidgetRefresh
import dev.alllexey.itmowidgets.core.storage.AppearancePreferences
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.RefreshStep
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.designsystem.theme.widgetPalette
import dev.alllexey.itmowidgets.feature.sport.data.widget.SportWidgetDataProvider
import dev.alllexey.itmowidgets.feature.sport.domain.widget.SportWidgetTimelineJson
import dev.alllexey.itmowidgets.feature.sport.widget.SportWidgetTriggers
import dev.alllexey.itmowidgets.feature.sport.widget.SportWidgetWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The sport widget's App Group timeline: [SportWidgetWriter] starts with the graph and writes on the main queue
 * (WidgetKit reloads are asked there) on session and widget theme changes and on request (an `AppGroupWidgetRefresh`,
 * which the iOS `ScheduleWidgetRefreshRequester` asks after a booking); the background runner publishes it as its sport widget step. The provider
 * is resolved at each write from the sport graph (`sportModule`, `sportIosModule`).
 */
val sportWidgetIosModule = module {
    single(createdAtStart = true) {
        val scope = this
        SportWidgetWriter(
            source = { until -> scope.get<SportWidgetDataProvider>().loadTimeline(until) },
            writer = AppGroupSnapshotWriter(get(), get(), SportWidgetTimelineJson.json),
            timeProvider = get(),
            log = get(),
            texts = get(),
            palette = { get<AppearancePreferences>().observeWidgetTheme().first()?.widgetPalette() },
        ).also { writer ->
            writer.launchIn(
                CoroutineScope(SupervisorJob() + get<AppDispatchers>().main),
                SportWidgetTriggers(
                    session = get<SessionRepository>().state.mapNotNull(::sessionKind),
                    widgetTheme = get<AppearancePreferences>().observeWidgetTheme(),
                ),
            )
        }
    }
    single<AppGroupWidgetRefresh>(named("sport-widget")) { get<SportWidgetWriter>() }
    single(named(RefreshStepKeys.SPORT_WIDGET)) {
        val writer = get<SportWidgetWriter>()
        RefreshStep(RefreshStepKeys.SPORT_WIDGET, RefreshStepKeys.SPORT_WIDGET_PERIOD) {
            writer.publish()
            CheckOutcome.DONE
        }
    }
}

/** Only settled sessions count: the writer then reads the tokens the provider reads. */
private fun sessionKind(state: SessionState): Any? = when (state) {
    is SessionState.SignedIn -> if (state.demo) "demo" else "signed-in"
    SessionState.SignedOut, SessionState.ReauthenticationRequired -> "signed-out"
    SessionState.Initializing, SessionState.SigningOut -> null
}
