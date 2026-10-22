package dev.alllexey.itmowidgets.feature.connections.domain

import dev.alllexey.itmowidgets.core.connections.PairLink
import dev.alllexey.itmowidgets.core.result.AppError
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/** What a pairing does: a new connection, or a new ITMO session (a fresh approval for the web) of an existing one. */
enum class PairingKind { CONNECT, RENEW }

/**
 * The plaintext sealed to an agent or an MCP server: its own ITMO.ID session, which the phone signed in for it. It
 * exists only between the sign-in and the seal; never logged or saved, so [toString] is redacted.
 */
@Serializable
class PairingBundle(
    val challengeId: Uuid,
    val kind: PairingKind,
    val isu: Int,
    val issuedAt: Instant,
    val itmo: BundleSession,
    val v: Int = VERSION,
) {
    override fun toString(): String = "PairingBundle(redacted)"

    companion object {
        const val VERSION = 1
    }
}

/** The client's ITMO.ID session inside a [PairingBundle]. */
@Serializable
class BundleSession(
    val sid: String,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
) {
    override fun toString(): String = "BundleSession(redacted)"
}

/**
 * HPKE (RFC 9180) base mode with DHKEM(X25519, HKDF-SHA256), HKDF-SHA256 and ChaCha20-Poly1305 and an empty AAD: Tink
 * on Android, CryptoKit on iOS. Returns the encapsulated key (32 bytes) followed by the ciphertext.
 */
fun interface HpkeSender {
    fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray
}

/** The phone's screen lock, asked before a client is connected or gets more access. */
fun interface DeviceUnlock {
    suspend fun unlock(): DeviceUnlockResult
}

enum class DeviceUnlockResult {
    UNLOCKED,
    CANCELLED,

    /** No screen lock is set, so there is nothing to confirm with. */
    NOT_SET_UP,
}

/** A client's pairing the phone opened; [scopes] are the requested ones, [connectionId] the renewed connection. */
data class PairingRequest(
    val challengeId: Uuid,
    val kind: PairingKind,
    val client: ClientType,
    val name: String,
    val version: String?,
    val platform: String?,
    val scopes: List<Scope>,
    val connectionId: Uuid?,
)

sealed interface PairingError {
    data class Failed(val error: AppError) : PairingError

    /** The link's key is not the one the client registered with Backend: a code of one QR with the key of another. */
    data object LinkMismatch : PairingError

    /** A kind or client type this version cannot pair. */
    data object Unsupported : PairingError

    /** The account has the most connections of this type Backend allows. */
    data object TooManyDevices : PairingError
}

sealed interface PairingResult<out T> {
    data class Success<out T>(val value: T) : PairingResult<T>

    data class Failure(val error: PairingError) : PairingResult<Nothing>
}

/** What the phone approves for a client; [session] and [sealedBundle] only for an agent or MCP server. */
class PairingApproval(
    val sealedBundle: String?,
    val scopes: List<String>?,
    val session: ItmoSessionRef?,
)

/**
 * Pairing on Backend. Every call fails with [AppError.CustomServicesDisabled] without the opt-in and
 * [AppError.DemoUnavailable] in the demo session.
 */
interface PairingRepository {

    /** Opens the code of [link]; the client's key must match Backend's commitment. */
    suspend fun open(link: PairLink): PairingResult<PairingRequest>

    /** Approves an opened pairing; returns the connection. */
    suspend fun approve(request: PairingRequest, approval: PairingApproval): PairingResult<Uuid>
}

/**
 * A new ITMO.ID session for a client, signed in by the user on the official page in a browser with its own empty
 * store ([userAgent] from the label switch). The page goes to [url]; every main-frame navigation goes through
 * [isCallback], and the callback URL to [finish], which checks the account and returns the session.
 */
interface ClientSignInAttempt {
    val url: String
    val userAgent: String

    fun isCallback(url: String): Boolean

    suspend fun finish(callbackUrl: String): ClientSignInResult
}

/** Starts sign-ins for clients; each [begin] is a new PKCE attempt. */
fun interface ClientSignIn {
    suspend fun begin(request: PairingRequest): ClientSignInAttempt
}

sealed interface ClientSignInResult {
    class Success(val session: ClientItmoSession) : ClientSignInResult

    /** The tokens belong to another ISU; that session is already ended. */
    data object OtherAccount : ClientSignInResult

    data class Failed(val error: AppError) : ClientSignInResult
}

/** A client's fresh ITMO.ID session, held only until it is sealed; [toString] is redacted. */
class ClientItmoSession(
    val isu: Int,
    val sid: String,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
) {
    override fun toString(): String = "ClientItmoSession(redacted)"
}
