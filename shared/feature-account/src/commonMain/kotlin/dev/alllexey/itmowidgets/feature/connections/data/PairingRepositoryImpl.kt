package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.client.connections.ApprovePairingRequest
import dev.alllexey.itmowidgets.client.connections.ConnectionsApi
import dev.alllexey.itmowidgets.client.connections.ItmoSession
import dev.alllexey.itmowidgets.client.connections.PairingKind as WireKind
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.connections.PairLink
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.PairingApproval
import dev.alllexey.itmowidgets.feature.connections.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.connections.domain.PairingError
import dev.alllexey.itmowidgets.feature.connections.domain.PairingKind
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

/**
 * Pairing through Core 2.0. A client's key never comes from Backend: an opened pairing is shown only when Backend's
 * commitment equals the one of the scanned [PairLink]. Backend's 409 (five live connections of the type) is
 * [PairingError.TooManyDevices]; a kind or client type this version does not know is [PairingError.Unsupported].
 */
class PairingRepositoryImpl(
    private val backend: BackendGate,
    private val api: ConnectionsApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : PairingRepository {

    override suspend fun open(link: PairLink): PairingResult<PairingRequest> {
        val preview = when (val opened = call { api.openPairing(link.code) }) {
            is PairingResult.Success -> opened.value
            is PairingResult.Failure -> return opened
        }
        if (!preview.keyCommitment.matches(link)) return PairingResult.Failure(PairingError.LinkMismatch)
        val kind = when (preview.kind) {
            WireKind.CONNECT -> PairingKind.CONNECT
            WireKind.RENEW -> PairingKind.RENEW
            WireKind.UNKNOWN -> return PairingResult.Failure(PairingError.Unsupported)
        }
        val client = preview.client.toDomain()
        if (client == ClientType.OTHER) return PairingResult.Failure(PairingError.Unsupported)
        return PairingResult.Success(
            PairingRequest(
                challengeId = preview.challengeId,
                kind = kind,
                client = client,
                name = preview.name.trim(),
                version = preview.version?.trim()?.ifEmpty { null },
                platform = preview.platform?.trim()?.ifEmpty { null },
                scopes = preview.requestedScopes.map { it.toDomain() },
                connectionId = preview.connectionId,
            ),
        )
    }

    override suspend fun approve(request: PairingRequest, approval: PairingApproval): PairingResult<Uuid> = call {
        api.approvePairing(
            request.challengeId,
            ApprovePairingRequest(
                sealedBundle = approval.sealedBundle,
                scopes = approval.scopes?.map { it.toWireScope() },
                itmo = approval.session?.let { ItmoSession(it.sid, it.expiresAt) },
            ),
        ).connectionId
    }

    private suspend fun <T> call(request: suspend () -> T): PairingResult<T> =
        when (val result = backendCall(demo, backend, dispatchers, request)) {
            is AppResult.Success -> PairingResult.Success(result.value)
            is AppResult.Failure -> PairingResult.Failure(
                if (result.error.isConflict()) PairingError.TooManyDevices else PairingError.Failed(result.error),
            )
        }

    private fun AppError.isConflict(): Boolean =
        ((this as? AppError.Unknown)?.cause as? BackendException.Http)?.status == CONFLICT

    /** Backend's base64url commitment against the one of the link's key and secret; a malformed one never matches. */
    private fun String.matches(link: PairLink): Boolean {
        val stored = try {
            BASE64.decode(this)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return stored.contentEquals(PairingCrypto.keyCommitment(link.publicKey, link.secret))
    }

    private companion object {
        const val CONFLICT = 409
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
