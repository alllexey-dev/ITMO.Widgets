package dev.alllexey.itmowidgets.di

import android.os.Build
import dev.alllexey.itmoapi.core.defaultEngine
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.BuildIdentity
import dev.alllexey.itmowidgets.client.ClientHeader
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DeviceApi
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.client.pairing.PairingApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.network.asBackendTokenSource
import dev.alllexey.itmowidgets.core.session.RefreshRejections
import dev.alllexey.itmowidgets.core.storage.InstallIdPreferences
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
            client = get()
        )
    }
    single<UsersApi> { get<BackendClient>().users }
    single<PairingApi> { get<BackendClient>().pairing }
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

// Build identity: plug in the BuildConfig fields of ci/simple-builds here (BUILD_CHANNEL `official|custom`,
// BUILD_SOURCE, BUILD_COMMIT, the upstream base version): channel = "custom" when BUILD_CHANNEL says so, else the
// flavor; source, commit and base as they are (empty means unknown).
internal fun buildIdentity(): BuildIdentity = BuildIdentity(channel = BuildConfig.FLAVOR)
