package dev.alllexey.itmowidgets.di

import dev.alllexey.itmoapi.core.defaultEngine
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.network.BackendClientFactory
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.session.RefreshRejections
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

val networkModule = module {
    // The one Ktor engine of MyItmoApi 2.x and Core 2.0: OkHttp without cookies, cache or redirects. Neither client
    // closes it; it lives as long as the process.
    single<HttpClientEngine> { defaultEngine() }
    single { RefreshRejections() }
    // MyItmoApi 2.x over the session's TokenStorage; its `tokens` is the only refresher and writer. A refresh ITMO.ID
    // rejects is reported to the session through RefreshRejections.
    single<MyItmoClient> {
        MyItmoClientFactory.create(
            storage = get(),
            engine = get(),
            clock = get(),
            refreshGuard = get<RefreshRejections>().guard()
        )
    }
    single { clientVersion() }
    // Core 2.0. Its token comes from the 2.x client's `tokens`; every request, workers' and FCM's included, carries
    // the ClientVersion.
    single<BackendClient> {
        BackendClientFactory.create(
            baseUrl = BuildConfig.WIDGETS_BASE_URL,
            tokens = get<MyItmoClient>().tokens,
            engine = get(),
            version = get()
        )
    }
    single<UsersApi> { get<BackendClient>().users }
    single<FriendsApi> { get<BackendClient>().friends }
    single<SportApi> { get<BackendClient>().sport }
    single<SubjectLinksApi> { get<BackendClient>().links }
    single<TeacherReviewsApi> { get<BackendClient>().reviews }
    single<DeviceApi> { get<BackendClient>().device }
    single<AppApi> { get<BackendClient>().app }
    single<ScheduleApi> { get<BackendClient>().schedule }
}

/** This build as Backend sees it in `X-App-Version`: `<versionName> (<versionCode>); android; <flavor>`. */
internal fun clientVersion(): ClientVersion =
    ClientVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString(), "android", BuildConfig.FLAVOR)
