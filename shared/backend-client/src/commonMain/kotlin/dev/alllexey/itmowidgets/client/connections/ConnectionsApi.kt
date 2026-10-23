package dev.alllexey.itmowidgets.client.connections

import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * The phone's side of connected clients: pairing (`/api/users/me/pair`) and «Устройства» (`/api/users/me/devices`,
 * `connections`, `installs`). Semantics are in Backend's `connections.md` contract. Scope texts follow the client's
 * `Accept-Language`. The client's own routes (`/api/pair/challenges`, `/api/client`) are not mirrored.
 */
interface ConnectionsApi {

    /**
     * `POST /api/users/me/pair/{code}/open`: binds the code to the caller (anyone else then gets 404) and extends it to
     * ten minutes. 404 `not_found` for an unknown, expired or foreign code; for `CONNECT` 403 `permission_denied` with
     * the client type off and 409 `business_rule_violation` with five live connections of the type.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun openPairing(code: String): PairingPreview

    /**
     * `POST /api/users/me/pair/{challengeId}/approve`, by the opener only; a repeated approval returns the same
     * connection. 404 for a challenge that is not open; 400 `invalid_request_data` when [request] breaks the rules of
     * the client type; the checks of [openPairing] run again.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun approvePairing(challengeId: Uuid, request: ApprovePairingRequest): ApprovedPairing

    /** `GET /api/users/me/devices`: connections (revoked ones with an open ITMO session included) and installs. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun devices(): UserDevices

    /**
     * `PUT /api/users/me/connections/{id}/scopes`: narrowing applies at once, widening only from the app; 400 for an
     * empty or foreign set, 404 for an unknown id.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun updateScopes(id: Uuid, request: UpdateScopesRequest): ConnectionInfo

    /** `POST /api/users/me/connections/{id}/revoke`: ends Backend access at once; the ITMO session stays. 404 unknown. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun revokeConnection(id: Uuid)

    /** `POST /api/users/me/connections/{id}/itmo-ended`: the phone ended the revoked connection's session; 409 live. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun connectionItmoEnded(id: Uuid)

    /** `POST /api/users/me/installs/{instanceId}/sign-out`: drops another install's push; 400 own, 404 unknown. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun signOutInstall(instanceId: Uuid)
}
