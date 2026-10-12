package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A pairing the phone opened, shown before it approves or rejects. [keyCommitment] is base64url
 * `SHA-256("iw-pair-commit-v1" || pk || s)`, `null` only for [PairingKind.WEB_SESSION]; the app compares it with the
 * scanned link and never trusts a key from Backend. [scopes] is set only for [PairingKind.AGENT_SESSION] (the agent
 * token's). [name] and [version] are the device's own, [userAgent] a browser's; each may be `null`.
 */
@Serializable
data class PairingPreview(
    @Serializable(with = UuidSerializer::class) val challengeId: Uuid,
    val kind: PairingKind,
    val name: String?,
    val version: String?,
    val userAgent: String?,
    val keyCommitment: String?,
    val scopes: List<AgentScope>?,
    @Serializable(with = WireInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val expiresAt: Instant,
)

/**
 * The approval of an opened pairing. Both keys are always sent: [sealedBundle] (base64url HPKE `enc || ciphertext`)
 * is required for the keyed kinds and forbidden for [PairingKind.WEB_SESSION]; [scopes] is required (non-empty) for
 * [PairingKind.AGENT] and forbidden otherwise.
 */
@Serializable
data class ApprovePairingRequest(
    val sealedBundle: String?,
    val scopes: List<AgentScope>?,
)

/**
 * One row of «Подключённые устройства»: an active web session or agent token of the caller. [live] and
 * [itmoSession] are an agent's (always false for the web); [current] marks the web session making the request, so
 * never the app's; [scopes] are an agent token's, `null` for the web.
 */
@Serializable
data class ConnectionInfo(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val kind: ConnectionKind,
    val name: String?,
    val userAgent: String?,
    @Serializable(with = WireInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val lastUsedAt: Instant?,
    val live: Boolean,
    val itmoSession: Boolean,
    val current: Boolean,
    val scopes: List<AgentScope>?,
)

/** What a pairing connects. Display: a kind a newer Backend adds decodes as [UNKNOWN], which the app never pairs. */
@Serializable(with = PairingKindSerializer::class)
enum class PairingKind {
    /** The web cabinet; no device key, no ITMO session. */
    WEB_SESSION,

    /** A new self-hosted agent: an agent token plus the phone's ITMO session. */
    AGENT,

    /** A connected agent asking only for a new ITMO session. */
    AGENT_SESSION,

    /** Later: a web version with its own ITMO access; not served yet. */
    WEB_ITMO,

    UNKNOWN,
}

/** What a connection is. Display: a newer kind decodes as [UNKNOWN] and shows as a generic device. */
@Serializable(with = ConnectionKindSerializer::class)
enum class ConnectionKind { WEB_SESSION, AGENT, UNKNOWN }

/** An agent token's permission; the wire values are lower case with a colon. Display: a newer scope is [UNKNOWN]. */
@Serializable(with = AgentScopeSerializer::class)
enum class AgentScope(internal val wire: String) {
    SPORT("sport"),
    FRIENDS_READ("friends:read"),
    SCHEDULE_WRITE("schedule:write"),
    UNKNOWN("UNKNOWN"),
}

internal object PairingKindSerializer :
    UnknownTolerantEnumSerializer<PairingKind>("PairingKind", PairingKind.entries, PairingKind.UNKNOWN)

internal object ConnectionKindSerializer :
    UnknownTolerantEnumSerializer<ConnectionKind>("ConnectionKind", ConnectionKind.entries, ConnectionKind.UNKNOWN)

internal object AgentScopeSerializer :
    UnknownTolerantEnumSerializer<AgentScope>("AgentScope", AgentScope.entries, AgentScope.UNKNOWN) {
    override fun wireName(value: AgentScope): String = value.wire
}
