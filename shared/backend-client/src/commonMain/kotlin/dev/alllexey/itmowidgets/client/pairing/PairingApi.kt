package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

/**
 * The phone's side of device pairing and the connections list (routes under `/api/users/me/pair` and
 * `/api/users/me/connections`). Semantics are in Backend's `pairing.md` contract. The pairing routes take only the
 * app's ITMO.ID bearer; the target's routes (under `/api/pair` and `/api/agent`) are not mirrored.
 */
interface PairingApi {

    /**
     * `POST /api/users/me/pair/{code}/open`: binds the code to the caller (anyone else then gets 404) and extends it to
     * ten minutes. 404 `not_found` for an unknown, expired or foreign code; for an `AGENT` code 403
     * `permission_denied` without agent access and 409 `business_rule_violation` with five active tokens.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun openPairing(code: String): PairingPreview

    /**
     * `POST /api/users/me/pair/{challengeId}/approve`, by the opener only. A repeated approval is a no-op; 404 for a
     * challenge that is not open and pending; 400 `invalid_request_data` when [request] breaks the kind's rules; the
     * `AGENT` checks of [openPairing] run again.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun approvePairing(challengeId: Uuid, request: ApprovePairingRequest)

    /** `POST /api/users/me/pair/{challengeId}/reject`: the device sees `REJECTED`; 404 as [approvePairing]. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun rejectPairing(challengeId: Uuid)

    /** `GET /api/users/me/connections`: active web sessions and agent tokens, most recently used first. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun connections(): List<ConnectionInfo>

    /**
     * `POST /api/users/me/connections/{id}/revoke`: ends Backend access at once; the device's ITMO session is not
     * ended. 404 for an unknown, foreign or already ended id.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun revokeConnection(id: Uuid)
}
