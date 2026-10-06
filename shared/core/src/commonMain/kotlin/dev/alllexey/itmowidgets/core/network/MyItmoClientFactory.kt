package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.itmoid.TokenRefreshGuard
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock

/**
 * Builds the one MyItmoApi 2.x client of the process. Each platform passes its engine (Ktor's OkHttp engine on
 * Android, the Darwin engine on iOS) and the storage that already holds the signed-in session; the client's
 * `tokens` is then the only proactive refresher and the only writer of that storage.
 *
 * [refreshGuard] serialises refreshes with other processes over the same storage: iOS passes its cross-process
 * lock, so the app and the notification service never both refresh; Android has one process and keeps the default.
 */
object MyItmoClientFactory {

    fun create(
        storage: TokenStorage,
        engine: HttpClientEngine,
        clock: Clock,
        configuration: MyItmoConfiguration = MyItmoConfiguration.DEFAULT,
        refreshGuard: TokenRefreshGuard = TokenRefreshGuard { it() },
    ): MyItmoClient = MyItmoClient(configuration, storage, engine, clock, refreshGuard)
}
