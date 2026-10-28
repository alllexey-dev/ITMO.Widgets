package dev.alllexey.itmowidgets.di

import android.os.Build
import dev.alllexey.itmoapi.core.defaultEngine
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.connections.ConnectionsApi
import dev.alllexey.itmowidgets.client.BuildIdentity
import dev.alllexey.itmowidgets.client.ClientHeader
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.network.asBackendTokenSource
import dev.alllexey.itmowidgets.core.platform.AppBuild
import dev.alllexey.itmowidgets.core.session.RefreshRejections
import dev.alllexey.itmowidgets.core.storage.InstallIdPreferences
import dev.alllexey.itmowidgets.core.text.APP_LANGUAGE
import io.ktor.client.engine.HttpClientEngine
import org.koin.dsl.module

val networkModule = module {
    // The one Ktor engine of MyItmoApi 2.x and Core 2.0: OkHttp without cookies, cache or redirects. Neither client
    // closes it; it lives as long as the process.
    single<HttpClientEngine> { defaultEngine() }
    single { RefreshRejections() }
    // The on-device ISU client over the same engine and the WebView's ITMO.ID cookies.
    single { IsuWebClient(get(), get()) }
    // MyItmoApi 2.x over the session's TokenStorage; its `tokens` is the only refresher and writer. A refresh ITMO.ID
    // rejects is reported to the session through RefreshRejections.
    single<MyItmoClient> {
        MyItmoClient(
            MyItmoConfiguration.DEFAULT,
            storage = get(),
            engine = get(),
            clock = get(),
            refreshGuard = get<RefreshRejections>().guard()
        )
    }
    single { clientVersion() }
    single { clientHeader(get()) }
    // Core 2.0. Its token comes from the 2.x client's `tokens`; every request, workers' and FCM's included, carries
    // the ClientVersion and the ClientHeader.
    single<BackendClient> {
        BackendClient(
            baseUrl = BuildConfig.WIDGETS_BASE_URL,
            tokens = get<MyItmoClient>().tokens.asBackendTokenSource(),
            engine = get(),
            version = get(),
            client = get(),
            language = { APP_LANGUAGE },
        )
    }
    single<UsersApi> { get<BackendClient>().users }
    single<ConnectionsApi> { get<BackendClient>().connections }
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

/** This build as Backend sees it in `X-Client` (ADR 0034): `kind=android; version=...; os=<API level>; install=...`. */
internal fun clientHeader(installIds: InstallIdPreferences): ClientHeader = ClientHeader(
    kind = "android",
    version = BuildConfig.VERSION_NAME,
    build = BuildConfig.VERSION_CODE.toString(),
    identity = buildIdentity(),
    os = Build.VERSION.SDK_INT.toString(),
    installId = installIds::installId
)

/**
 * The build identity of docs/building.md in `X-Client`: the flavor (`github`, `play`) as the channel of an official
 * build, `custom` for any other; the building repository or `local`; the commit, left out when empty (debug builds).
 * No base: VERSION_NAME already is the upstream version.
 */
internal fun buildIdentity(
    flavor: String = BuildConfig.FLAVOR,
    buildChannel: String = BuildConfig.BUILD_CHANNEL,
    source: String = BuildConfig.BUILD_SOURCE,
    commit: String = BuildConfig.BUILD_COMMIT,
): BuildIdentity = BuildIdentity(
    channel = if (buildChannel == OFFICIAL_BUILD) flavor else CUSTOM_CHANNEL,
    source = source.ifEmpty { null },
    commit = commit.ifEmpty { null },
)

/** This build for the settings version row: the version and the identity `X-Client` sends. */
internal fun appBuild(identity: BuildIdentity = buildIdentity()): AppBuild = AppBuild(
    version = BuildConfig.VERSION_NAME,
    build = BuildConfig.VERSION_CODE.toString(),
    channel = identity.channel,
    source = identity.source,
    commit = identity.commit,
)

private const val OFFICIAL_BUILD = "official"
private const val CUSTOM_CHANNEL = AppBuild.CUSTOM
