package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.client.connections.ConnectionsApi
import dev.alllexey.itmowidgets.client.connections.UpdateScopesRequest
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * «Устройства» through Core 2.0's connections area. The last answer stays in memory, so the screen, Профиль and the
 * reminder draw it at once; a change applies to it without a reload.
 */
class DevicesRepositoryImpl(
    private val backend: BackendGate,
    private val api: ConnectionsApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : DevicesRepository {
    private val last = MutableStateFlow<Devices?>(null)
    override val devices: StateFlow<Devices?> = last.asStateFlow()

    override suspend fun refresh(): AppResult<Devices> = call {
        val answer = api.devices()
        Devices(answer.connections.map { it.toDomain() }, answer.installs.map { it.toDomain() })
    }.also { if (it is AppResult.Success) last.value = it.value }

    override suspend fun updateScopes(id: Uuid, scopes: List<String>): AppResult<Connection> = call {
        api.updateScopes(id, UpdateScopesRequest(scopes.map { it.toWireScope() })).toDomain()
    }.also { result ->
        if (result is AppResult.Success) {
            last.update { it?.copy(connections = it.connections.map { old -> if (old.id == id) result.value else old }) }
        }
    }

    override suspend fun revoke(id: Uuid): AppResult<Unit> = call { api.revokeConnection(id) }
        .also { if (it is AppResult.Success) last.update { d -> d?.copy(connections = d.connections.filter { c -> c.id != id }) } }

    override suspend fun itmoEnded(id: Uuid): AppResult<Unit> = call { api.connectionItmoEnded(id) }

    override suspend fun signOutInstall(instanceId: Uuid): AppResult<Unit> = call { api.signOutInstall(instanceId) }
        .also {
            if (it is AppResult.Success) {
                last.update { d -> d?.copy(installs = d.installs.filter { i -> i.instanceId != instanceId }) }
            }
        }

    private suspend fun <T> call(request: suspend () -> T): AppResult<T> =
        backendCall(demo, backend, dispatchers, request)
}
