package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock

/**
 * Builds the one MyItmoApi 2.x client of the process. Each platform passes its engine (Ktor's OkHttp engine on
 * Android, the Darwin engine on iOS) and the storage that already holds the signed-in session; the client's
 * `tokens` is then the only proactive refresher and the only writer of that storage.
 */
object MyItmoClientFactory {

    fun create(
        storage: TokenStorage,
        engine: HttpClientEngine,
        clock: Clock,
        configuration: MyItmoConfiguration = MyItmoConfiguration.DEFAULT,
    ): MyItmoClient = MyItmoClient(configuration, storage, engine, clock)
}
