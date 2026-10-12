package dev.alllexey.itmowidgets.feature.weblogin.domain

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** What an agent may do with its token; the switches of the confirm card. */
enum class AgentScope { SPORT, FRIENDS_READ, SCHEDULE_WRITE }

/** A device's pairing the phone opened. [scopes] are an agent session's existing ones, empty for other kinds. */
data class PairingRequest(
    val challengeId: Uuid,
    val kind: PairingKind,
    /** The name the device chose for itself. */
    val name: String?,
    /** An agent's version. */
    val version: String?,
    /** A browser's User-Agent. */
    val userAgent: String?,
    val scopes: Set<AgentScope>,
    val createdAt: Instant,
)

enum class ConnectionKind { WEB, AGENT, OTHER }

/** One row of «Подключённые устройства». */
data class Connection(
    val id: Uuid,
    val kind: ConnectionKind,
    val name: String?,
    val userAgent: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    /** The device holds this phone's ITMO session. */
    val itmoSession: Boolean,
)

sealed interface PairingError {
    data class Failed(val error: AppError) : PairingError

    /** The link's key is not the one the device registered with Backend: a code of one QR with the key of another. */
    data object LinkMismatch : PairingError

    /** A typed code of a device that takes an ITMO session: its key comes only from the QR or the link. */
    data object KeyRequired : PairingError

    /** A kind this version cannot pair. */
    data object Unsupported : PairingError

    /** The account has the most agents Backend allows. */
    data object TooManyDevices : PairingError
}

sealed interface PairingResult<out T> {
    data class Success<out T>(val value: T) : PairingResult<T>

    data class Failure(val error: PairingError) : PairingResult<Nothing>
}

/**
 * Pairing and the connections list on Backend. Every call fails with
 * [AppError.CustomServicesDisabled] without the opt-in and [AppError.DemoUnavailable] in the demo session.
 */
interface PairingRepository {

    /**
     * Opens [code]: with the [link] it came from, the device's key must match Backend's commitment; without one (a
     * typed code) a keyed kind is [PairingError.KeyRequired].
     */
    suspend fun open(code: String, link: PairLink?): PairingResult<PairingRequest>

    /** Approves an opened keyed pairing; [scopes] only for [PairingKind.AGENT]. */
    suspend fun approve(request: PairingRequest, sealedBundle: String, scopes: Set<AgentScope>): PairingResult<Unit>

    suspend fun reject(challengeId: Uuid): AppResult<Unit>

    suspend fun connections(): AppResult<List<Connection>>

    suspend fun revoke(id: Uuid): AppResult<Unit>
}
