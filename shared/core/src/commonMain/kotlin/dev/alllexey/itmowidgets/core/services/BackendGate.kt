package dev.alllexey.itmowidgets.core.services

import kotlinx.coroutines.flow.Flow

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
