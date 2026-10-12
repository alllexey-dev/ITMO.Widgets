package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.pairing.ApprovePairingRequest
import dev.alllexey.itmowidgets.client.pairing.ConnectionInfo
import dev.alllexey.itmowidgets.client.pairing.PairingApi
import dev.alllexey.itmowidgets.client.pairing.PairingPreview
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.Connection
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingError
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingResult
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid
import kotlinx.coroutines.withContext
import dev.alllexey.itmowidgets.client.pairing.AgentScope as WireScope
import dev.alllexey.itmowidgets.client.pairing.ConnectionKind as WireConnectionKind
import dev.alllexey.itmowidgets.client.pairing.PairingKind as WireKind

/**
 * Pairing and connections through Core 2.0. The demo session is refused before the opt-in is read, so it never
 * reaches Backend. A device's key never comes from Backend: an opened pairing is shown only when Backend's commitment
 * equals the one computed from the scanned [PairLink], and a typed code of a keyed kind is refused. Backend's 409 for
 * a new agent (five active tokens) is [PairingError.TooManyDevices].
 */
class PairingRepositoryImpl(
    private val backend: BackendGate,
    private val pairing: PairingApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : PairingRepository {

    override suspend fun open(code: String, link: PairLink?): PairingResult<PairingRequest> {
        val preview = when (val opened = pairingCall { pairing.openPairing(code) }) {
            is PairingResult.Success -> opened.value
            is PairingResult.Failure -> return opened
        }
        val kind = preview.kind.toDomain()
        val refusal = when {
            kind == PairingKind.WEB_SESSION -> PairingError.Unsupported.takeIf { link != null }
            link == null -> PairingError.KeyRequired
            !preview.keyCommitment.matches(link) -> PairingError.LinkMismatch
            kind == null || kind == PairingKind.WEB_ITMO -> PairingError.Unsupported
            else -> null
        }
        if (refusal != null) return PairingResult.Failure(refusal)
        return PairingResult.Success(preview.toModel(checkNotNull(kind)))
    }

    override suspend fun approve(
        request: PairingRequest,
        sealedBundle: String,
        scopes: Set<AgentScope>,
    ): PairingResult<Unit> {
        val wireScopes = scopes.map { it.toWire() }.takeIf { request.kind == PairingKind.AGENT }
        return pairingCall {
            pairing.approvePairing(request.challengeId, ApprovePairingRequest(sealedBundle, wireScopes))
        }
    }

    override suspend fun reject(challengeId: Uuid): AppResult<Unit> = call { pairing.rejectPairing(challengeId) }

    override suspend fun connections(): AppResult<List<Connection>> =
        call { pairing.connections().map { it.toModel() } }

    override suspend fun revoke(id: Uuid): AppResult<Unit> = call { pairing.revokeConnection(id) }

    private suspend fun <T> call(request: suspend () -> T): AppResult<T> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return appResultOf(::backendAppError) { withContext(dispatchers.io) { request() } }
    }

    private suspend fun <T> pairingCall(request: suspend () -> T): PairingResult<T> =
        when (val result = call(request)) {
            is AppResult.Success -> PairingResult.Success(result.value)
            is AppResult.Failure -> PairingResult.Failure(
                if (result.error.isConflict()) PairingError.TooManyDevices else PairingError.Failed(result.error),
            )
        }

    private fun AppError.isConflict(): Boolean =
        ((this as? AppError.Unknown)?.cause as? BackendException.Http)?.status == CONFLICT

    /** Backend's base64url commitment against the one of the link's key and secret; a malformed one never matches. */
    private fun String?.matches(link: PairLink): Boolean {
        val stored = try {
            BASE64.decode(this ?: return false)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return stored.contentEquals(PairingCrypto.keyCommitment(link.publicKey, link.secret))
    }

    private fun PairingPreview.toModel(kind: PairingKind) = PairingRequest(
        challengeId = challengeId,
        kind = kind,
        name = name?.trim()?.ifEmpty { null },
        version = version?.trim()?.ifEmpty { null },
        userAgent = userAgent?.trim()?.ifEmpty { null },
        scopes = scopes.orEmpty().mapNotNull { it.toDomain() }.toSet(),
        createdAt = createdAt,
    )

    private fun ConnectionInfo.toModel() = Connection(
        id = id,
        kind = when (kind) {
            WireConnectionKind.WEB_SESSION -> ConnectionKind.WEB
            WireConnectionKind.AGENT -> ConnectionKind.AGENT
            WireConnectionKind.UNKNOWN -> ConnectionKind.OTHER
        },
        name = name?.trim()?.ifEmpty { null },
        userAgent = userAgent?.trim()?.ifEmpty { null },
        createdAt = createdAt,
        lastUsedAt = lastUsedAt,
        itmoSession = itmoSession,
    )

    private companion object {
        const val CONFLICT = 409
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        fun WireKind.toDomain(): PairingKind? = when (this) {
            WireKind.WEB_SESSION -> PairingKind.WEB_SESSION
            WireKind.AGENT -> PairingKind.AGENT
            WireKind.AGENT_SESSION -> PairingKind.AGENT_SESSION
            WireKind.WEB_ITMO -> PairingKind.WEB_ITMO
            WireKind.UNKNOWN -> null
        }

        fun WireScope.toDomain(): AgentScope? = when (this) {
            WireScope.SPORT -> AgentScope.SPORT
            WireScope.FRIENDS_READ -> AgentScope.FRIENDS_READ
            WireScope.SCHEDULE_WRITE -> AgentScope.SCHEDULE_WRITE
            WireScope.UNKNOWN -> null
        }

        fun AgentScope.toWire(): WireScope = when (this) {
            AgentScope.SPORT -> WireScope.SPORT
            AgentScope.FRIENDS_READ -> WireScope.FRIENDS_READ
            AgentScope.SCHEDULE_WRITE -> WireScope.SCHEDULE_WRITE
        }
    }
}
