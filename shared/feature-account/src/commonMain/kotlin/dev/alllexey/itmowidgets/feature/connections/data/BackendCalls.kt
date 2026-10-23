package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.client.connections.ConnectionClient
import dev.alllexey.itmowidgets.client.connections.ConnectionInfo
import dev.alllexey.itmowidgets.client.connections.ConnectionScope
import dev.alllexey.itmowidgets.client.connections.InstallInfo
import dev.alllexey.itmowidgets.client.connections.ItmoStatus
import dev.alllexey.itmowidgets.client.connections.ScopeInfo
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.connections.domain.AppInstall
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoHealth
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoSessionRef
import dev.alllexey.itmowidgets.feature.connections.domain.Scope
import dev.alllexey.itmowidgets.feature.connections.domain.ScopeGrant
import kotlinx.coroutines.withContext

/** The demo session first, then the opt-in, then the call on IO with the released error mapping. */
internal suspend fun <T> backendCall(
    demo: DemoMode,
    backend: BackendGate,
    dispatchers: AppDispatchers,
    request: suspend () -> T,
): AppResult<T> {
    if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
    if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
    return appResultOf(::backendAppError) { withContext(dispatchers.io) { request() } }
}

internal fun ConnectionClient.toDomain(): ClientType = when (this) {
    ConnectionClient.AGENT -> ClientType.AGENT
    ConnectionClient.MCP -> ClientType.MCP
    ConnectionClient.WEB -> ClientType.WEB
    ConnectionClient.UNKNOWN -> ClientType.OTHER
}

/** The domain's scope id is the wire enum's name; an unknown scope has none and cannot be chosen. */
internal fun ScopeInfo.toDomain() = Scope(id.takeIf { it != ConnectionScope.UNKNOWN }?.name, title, description)

internal fun String.toWireScope(): ConnectionScope = ConnectionScope.valueOf(this)

internal fun ConnectionInfo.toDomain() = Connection(
    id = id,
    client = client.toDomain(),
    name = name.trim(),
    version = version?.trim()?.ifEmpty { null },
    platform = platform?.trim()?.ifEmpty { null },
    scopes = scopes.map { ScopeGrant(it.scope.toDomain(), it.granted) },
    createdAt = createdAt,
    approvedAt = approvedAt,
    lastUsedAt = lastUsedAt,
    itmo = itmo?.let { ItmoSessionRef(it.sid, it.expiresAt) },
    health = when (itmoStatus) {
        ItmoStatus.OK -> ItmoHealth.OK
        ItmoStatus.CLOSED -> ItmoHealth.CLOSED
        ItmoStatus.NONE, ItmoStatus.UNKNOWN -> ItmoHealth.NONE
    },
    revokedAt = revokedAt,
)

internal fun InstallInfo.toDomain() = AppInstall(
    instanceId = instanceId,
    ios = platform == DevicePlatform.IOS,
    deviceName = deviceName?.trim()?.ifEmpty { null },
    os = os?.trim()?.ifEmpty { null },
    version = version,
    firstSeenAt = firstSeenAt,
    lastSeenAt = lastSeenAt,
    pushEnabled = pushEnabled,
    sid = sid,
    current = current,
)
