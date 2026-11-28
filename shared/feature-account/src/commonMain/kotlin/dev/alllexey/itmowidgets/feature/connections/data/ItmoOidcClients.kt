package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmoapi.itmoid.ItmoIdClient
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoGrants
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock

/**
 * The ITMO OIDC clients whose refresh grants this version mints (ADR 0040), by client id: `student-personal-cabinet`
 * through the app's own [MyItmoClient.identity], the development stand's `student-personal-cabinet-dev` through a
 * stateless MyItmoApi [ItmoIdClient] with `MyItmoConfiguration.DEV` on the same engine. Every id of
 * [ItmoGrants.oidcClients] has one.
 */
class ItmoOidcClients(myItmo: MyItmoClient, engine: HttpClientEngine, clock: Clock) {
    private val clients: Map<String, ItmoIdClient> = mapOf(
        ItmoGrants.MY_ITMO to myItmo.identity,
        ItmoGrants.MY_ITMO_DEV to ItmoIdClient(engine, clock, MyItmoConfiguration.DEV.itmoId),
    )

    fun client(id: String): ItmoIdClient? = clients[id]

    override fun toString(): String = "ItmoOidcClients(${clients.keys})"
}
