package dev.alllexey.itmowidgets.feature.sport.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.feature.sport.data.bell.AppGroupSportResultInbox
import dev.alllexey.itmowidgets.feature.sport.data.bell.SportResultInbox
import org.koin.core.qualifier.named
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverrideSource
import dev.alllexey.itmowidgets.feature.sport.ui.SportShares
import org.koin.dsl.module

/**
 * The iOS bindings of [sportModule], what `androidSportModule` and the app's other Koin modules give Android; load it
 * with that module.
 * Core 2.0's sport area comes from the one `BackendClient`; the debug ports answer nothing, since the developer tools
 * stay Android-only; [SportShares] sends a lesson's link through the share sheet. The rest (MyItmoApi, the Backend
 * gate, time, dispatchers, the application scope, the share links, `DemoMode`, the sport-sign preferences, the
 * services opt-in, the widget refresh, the schedule refresh after a booking, `FriendRepository`) comes from the core,
 * account, settings, schedule widget, schedule data and friend picker modules. The queue results the notification
 * service leaves in the App Group reach the bell through [AppGroupSportResultInbox].
 */
val sportIosModule = module {
    single<SportApi> { get<BackendClient>().sport }
    single<SportLessonTemplateProvider> { NoSportLessonTemplates }
    single<SportScoreOverrideSource> { SportScoreOverrideSource { null } }
    single { SportShares(get(), get(), get(), get()) }
    // The notification service's queue results reach the bell through the App Group; sign-out drops the rest.
    single { AppGroupSportResultInbox(get<AppGroupDirectory>(), get()) }
    single<SportResultInbox> { get<AppGroupSportResultInbox>() }
    single<SessionDataCleaner>(named("sport-results-inbox")) { get<AppGroupSportResultInbox>() }
}

/** No debug template lessons: the developer tools that set them are Android-only. */
private object NoSportLessonTemplates : SportLessonTemplateProvider {
    override fun getSchedule() = null
}
