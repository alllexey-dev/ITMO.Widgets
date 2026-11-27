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

/**
 * The plaintext sealed to a client of a protocol-2 pairing (ADR 0040): [itmo] holds exactly the granted `itmo:*`
 * items in request order, all from one ITMO.ID session; keys in the wire spec's order, [v] first. Protocol 1 keeps
 * [PairingBundle] byte for byte. Never logged or saved, so [toString] is redacted.
 */
@Serializable
class PairingBundleV2(
    val v: Int = VERSION,
    val challengeId: Uuid,
    val kind: PairingKind,
    val clientId: String,
    val isu: Int,
    val issuedAt: Instant,
    val itmo: BundleItmo,
) {
    override fun toString(): String = "PairingBundleV2(redacted)"

    companion object {
        const val VERSION = 2
    }
}

/** The session of a [PairingBundleV2] and its granted items. */
@Serializable
class BundleItmo(val sid: String, val expiresAt: Instant, val items: List<BundleItem>) {
    override fun toString(): String = "BundleItmo(redacted)"
}

/** One granted ITMO grant: [grant] is its id, [value] the credential. */
@Serializable
class BundleItem(val grant: String, val value: String, val expiresAt: Instant) {
    override fun toString(): String = "BundleItem(grant=$grant, redacted)"
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

/**
 * A client's pairing the phone opened; [scopes] are the requested ones, [connectionId] the renewed connection. A
 * protocol-2 pairing also has its registry entry [clientId] and [appName] and the requested [grants] in registry
 * order; [scopes] then only mirror the `widgets:*` part.
 */
data class PairingRequest(
    val challengeId: Uuid,
    val kind: PairingKind,
    val client: ClientType,
    val name: String,
    val version: String?,
    val platform: String?,
    val scopes: List<Scope>,
    val connectionId: Uuid?,
    val protocol: Int = 1,
    val clientId: String? = null,
    val appName: String? = null,
    val grants: List<Grant> = emptyList(),
)

/**
 * One grant of a protocol-2 pairing: a `widgets:*` one with Backend's [title] and [description], or an `itmo:*` one
 * whose texts are the app's. [purpose] is the registry's; [held] is set on a renewal: whether the connection already
 * holds that ITMO grant.
 */
data class Grant(
    val id: String,
    val required: Boolean,
    val title: String?,
    val description: String?,
    val purpose: String?,
    val held: Boolean?,
) {
    val itmo: Boolean get() = ItmoGrants.isItmo(id)
}

/**
 * The ITMO grants this version can mint (ADR 0040). Any other `itmo:*` id is unknown to it: shown as unavailable, and
 * a pairing that requires one is refused.
 */
object ItmoGrants {
    /** The refresh token of the client's own ITMO.ID session for MyITMO's client. */
    const val REFRESH = "itmo:refresh:student-personal-cabinet"

    /** That session's `KEYCLOAK_IDENTITY`: every ITMO service as the user. */
    const val SSO = "itmo:sso"

    val known: Set<String> = setOf(REFRESH, SSO)

    fun isItmo(id: String): Boolean = id.startsWith("itmo:")

    fun isKnown(id: String): Boolean = id in known
}

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

/**
 * What the phone approves for a client; [session] and [sealedBundle] only when an ITMO session is sealed. Protocol 1
 * sends [scopes], protocol 2 [granted] (the chosen grant ids, every required one included).
 */
class PairingApproval(
    val sealedBundle: String?,
    val scopes: List<String>?,
    val session: ItmoSessionRef?,
    val granted: List<String>? = null,
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
 * [isCallback], and the callback URL to [finish], which checks the account and returns the session. [withIdentity]
 * asks for the session's `KEYCLOAK_IDENTITY` too: the host reads it from the isolated store when it catches the
 * callback, before the store goes, and passes it to [finish].
 */
interface ClientSignInAttempt {
    val url: String
    val userAgent: String
    val withIdentity: Boolean

    fun isCallback(url: String): Boolean

    suspend fun finish(callbackUrl: String, identityCookie: String?): ClientSignInResult
}

/** Starts sign-ins for clients; each [begin] is a new PKCE attempt, [withIdentity] for an `itmo:sso` grant. */
fun interface ClientSignIn {
    suspend fun begin(request: PairingRequest, withIdentity: Boolean): ClientSignInAttempt
}

sealed interface ClientSignInResult {
    class Success(val session: ClientItmoSession) : ClientSignInResult

    /** The tokens belong to another ISU; that session is already ended. */
    data object OtherAccount : ClientSignInResult

    /**
     * The `KEYCLOAK_IDENTITY` the user granted was missing or of another session; that session is already ended, as
     * silently dropping a granted item would be wrong.
     */
    data object IdentityMissing : ClientSignInResult

    data class Failed(val error: AppError) : ClientSignInResult
}

/**
 * A client's fresh ITMO.ID session, held only until it is sealed; [toString] is redacted. [identity] is its
 * `KEYCLOAK_IDENTITY` when the attempt asked for it.
 */
class ClientItmoSession(
    val isu: Int,
    val sid: String,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
    val identity: ItmoIdentity? = null,
) {
    override fun toString(): String = "ClientItmoSession(redacted)"
}

/**
 * A session's `KEYCLOAK_IDENTITY` cookie (a JWT signed by ITMO.ID, read without checking the signature) and its
 * `exp`. In memory only between the sign-in and the seal; never logged, so [toString] is redacted.
 */
class ItmoIdentity(val value: String, val expiresAt: Instant) {
    override fun toString(): String = "ItmoIdentity(redacted)"
}
