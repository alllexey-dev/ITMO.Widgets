package dev.alllexey.itmowidgets.core.services

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The iOS [BackendGate], answering as Android's `DefaultBackendGate` (still in `:app`, Hilt-constructed): the demo
 * session shows as connected and never calls Backend.
 */
class IosBackendGate(
    private val servicesOptIn: ServicesOptInPreferences,
    private val demo: DemoMode
) : BackendGate {

    override suspend fun isConnected(): Boolean = demo.isActive() || isOptedIn()

    override fun observeConnected(): Flow<Boolean> =
        combine(demo.observeActive(), servicesOptIn.observeCustomServicesEnabled()) { demo, optedIn -> demo || optedIn }
            .distinctUntilChanged()

    override suspend fun mayCallBackend(): Boolean = !demo.isActive() && isOptedIn()

    override suspend fun isOptedIn(): Boolean = servicesOptIn.getCustomServicesEnabled()
}
