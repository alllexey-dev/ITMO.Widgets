package dev.alllexey.itmowidgets.core.services

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The one place that reads the ITMO.Widgets Backend opt-in.
 *
 * It answers three questions that must stay apart: merging "shows as connected" with "may call Backend"
 * either sends demo requests to Backend or hides the demo session's social data.
 */
interface BackendGate {

    /** Shows as connected: the demo session or the stored opt-in. Settings and the social screens read it. */
    suspend fun isConnected(): Boolean

    fun observeConnected(): Flow<Boolean>

    /** The stored opt-in outside the demo session. Every `ItmoWidgetsApi` call needs it. */
    suspend fun mayCallBackend(): Boolean

    /** The stored opt-in alone, for the widget and the push delivery, which never treat the demo as connected. */
    suspend fun isOptedIn(): Boolean
}

class DefaultBackendGate @Inject constructor(
    private val settings: AppSettingsStorage,
    private val demo: DemoMode
) : BackendGate {

    override suspend fun isConnected(): Boolean = demo.isActive() || isOptedIn()

    override fun observeConnected(): Flow<Boolean> =
        combine(demo.observeActive(), settings.observeCustomServicesEnabled()) { demo, optedIn -> demo || optedIn }
            .distinctUntilChanged()

    override suspend fun mayCallBackend(): Boolean = !demo.isActive() && isOptedIn()

    override suspend fun isOptedIn(): Boolean = settings.getCustomServicesEnabled()
}
