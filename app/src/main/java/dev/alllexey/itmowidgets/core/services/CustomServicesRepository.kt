package dev.alllexey.itmowidgets.core.services

import kotlinx.coroutines.flow.Flow

/**
 * Access to the custom services opt-in.
 *
 * The flag gates every ITMO.Widgets Backend call: while it is disabled the
 * application never sends the MyITMO access token to the project backend.
 */
interface CustomServicesRepository {

    fun observeEnabled(): Flow<Boolean>

    /** One-shot read for data-layer gates that must not touch the backend. */
    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)
}
