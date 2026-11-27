package dev.alllexey.itmowidgets.client.connections

import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.client.json.UnknownTolerantEnumSerializer
import dev.alllexey.itmowidgets.client.json.UuidSerializer
import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A pairing the phone opened, shown before it approves. [keyCommitment] is base64url
 * `SHA-256("iw-pair-commit-v1" || pk || s)`: the app compares it with the scanned link and never trusts a key from
 * Backend. [requestedScopes] carry Backend's texts in the request's `Accept-Language`; [connectionId] is set for
 * [PairingKind.RENEW]. [name], [version] and [platform] are the client's own.
 *
 * Opened with `?protocol=2` (the app always is), the preview also carries [protocol] (1 or 2), the registry entry
 * [app] and the requested [grants] in registry order (ADR 0040). They are nullable so an older Backend still decodes.
 */
@Serializable
data class PairingPreview(
    @Serializable(with = UuidSerializer::class) val challengeId: Uuid,
    val kind: PairingKind,
    val client: ConnectionClient,
    val name: String,
    val version: String?,
    val platform: String?,
    val requestedScopes: List<ScopeInfo>,
    val keyCommitment: String,
    @Serializable(with = UuidSerializer::class) val connectionId: Uuid?,
    @Serializable(with = WireInstantSerializer::class) val expiresAt: Instant,
    val protocol: Int? = null,
    val app: ClientAppInfo? = null,
    val grants: List<GrantRequest>? = null,
)

/**
 * A client app of Backend's registry as the phone shows it. [verified] is false for every client of step 1 (public
 * clients cannot prove who they are).
 */
@Serializable
data class ClientAppInfo(
    val id: String,
    val name: String,
    val homepage: String? = null,
    val iconUrl: String? = null,
    val deployment: ClientDeployment,
    val verified: Boolean = false,
)

/**
 * One grant a protocol-2 pairing requests: `widgets:<scope>` (Backend's [title] and [description]) or `itmo:*` (no
 * texts: the app owns them). [purpose] is the registry's «what this client does with it»; [granted] is set only on a
 * renewal: whether the connection already holds that ITMO grant.
 */
@Serializable
data class GrantRequest(
    val id: String,
    val required: Boolean,
    val title: String? = null,
    val description: String? = null,
    val purpose: String? = null,
    val granted: Boolean? = null,
)

/**
 * The approval of an opened pairing; nulls are omitted. Protocol 1: [sealedBundle] (base64url HPKE
 * `enc || ciphertext`) and [itmo] are required for agents and MCP servers and forbidden for the web; [scopes] only on
 * [PairingKind.CONNECT], a non-empty subset of the request, `null` keeps them all; [granted] is `null`. Protocol 2:
 * [granted] lists the grant ids the user chose (every required one included), [scopes] is `null`, and
 * [sealedBundle] and [itmo] are sent exactly when [granted] has an `itmo:*` id.
 */
@Serializable
data class ApprovePairingRequest(
    val sealedBundle: String?,
    val scopes: List<ConnectionScope>?,
    val itmo: ItmoSession?,
    val granted: List<String>?,
)

/** The connection an approval created or renewed. */
@Serializable
data class ApprovedPairing(@Serializable(with = UuidSerializer::class) val connectionId: Uuid)

/** A connection's ITMO.ID session as the phone reported it: the `sid` and when the session ends. */
@Serializable
data class ItmoSession(
    val sid: String,
    @Serializable(with = WireInstantSerializer::class) val expiresAt: Instant,
) {
    override fun toString(): String = "ItmoSession(sid=<redacted>, expiresAt=$expiresAt)"
}

/** A scope with Backend's texts; [id] is what a choice sends back. */
@Serializable
data class ScopeInfo(
    val id: ConnectionScope,
    val title: String,
    val description: String,
)

/** One scope the client type allows and whether this connection holds it. */
@Serializable
data class ScopeGrant(val scope: ScopeInfo, val granted: Boolean)

/**
 * A connected client. [scopes] lists every scope its type allows, in Backend's order. [itmo] is `null` for the web;
 * [itmoStatus] is the client's own report. [revokedAt] is set on a revoked connection whose ITMO session the phone
 * has not ended yet: the app hides such rows and ends their sessions. [clientId] and [app] name its registry entry,
 * [itmoGrants] the `itmo:*` grants the phone sealed to it (a label Backend cannot verify); all three are nullable so
 * an older Backend still decodes.
 */
@Serializable
data class ConnectionInfo(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val client: ConnectionClient,
    val name: String,
    val version: String?,
    val platform: String?,
    val scopes: List<ScopeGrant>,
    @Serializable(with = WireInstantSerializer::class) val createdAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val approvedAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val lastUsedAt: Instant?,
    val live: Boolean,
    val itmo: ItmoSession?,
    val itmoStatus: ItmoStatus,
    @Serializable(with = WireInstantSerializer::class) val revokedAt: Instant?,
    val clientId: String? = null,
    val app: ClientAppInfo? = null,
    val itmoGrants: List<String>? = null,
)

/**
 * One app install of the user. [sid] is its ITMO.ID session from its last ID-token sync; [current] is the calling
 * install. [os] is the Android API level or the iOS major version.
 */
@Serializable
data class InstallInfo(
    @Serializable(with = UuidSerializer::class) val instanceId: Uuid,
    val platform: DevicePlatform,
    val deviceName: String?,
    val os: String?,
    val version: String,
    @Serializable(with = WireInstantSerializer::class) val firstSeenAt: Instant,
    @Serializable(with = WireInstantSerializer::class) val lastSeenAt: Instant,
    val pushEnabled: Boolean,
    /** When the install last signed out and dropped its push registration; null if it never did. Not shown yet. */
    @Serializable(with = WireInstantSerializer::class) val signedOutAt: Instant? = null,
    val sid: String?,
    val current: Boolean,
)

/** `GET /api/users/me/devices`: connections, most recently used first, and installs, most recently seen first. */
@Serializable
data class UserDevices(
    val connections: List<ConnectionInfo>,
    val installs: List<InstallInfo>,
)

/** The scopes a connection keeps: non-empty, within its type's allowed set. */
@Serializable
data class UpdateScopesRequest(val scopes: List<ConnectionScope>)

/** What a pairing does. Display: a newer kind decodes as [UNKNOWN], which the app never approves. */
@Serializable(with = PairingKindSerializer::class)
enum class PairingKind { CONNECT, RENEW, UNKNOWN }

/** The client type of a connection; wire values are lower case. Display: a newer type is [UNKNOWN]. */
@Serializable(with = ConnectionClientSerializer::class)
enum class ConnectionClient(internal val wire: String) {
    AGENT("agent"),
    MCP("mcp"),
    WEB("web"),
    UNKNOWN("UNKNOWN"),
}

/** A connection's Widgets permission; wire values are lower case with a colon. Display: a newer one is [UNKNOWN]. */
@Serializable(with = ConnectionScopeSerializer::class)
enum class ConnectionScope(internal val wire: String) {
    SPORT("sport"),
    FRIENDS_READ("friends:read"),
    SCHEDULE_WRITE("schedule:write"),
    REVIEWS_READ("reviews:read"),
    LINKS_READ("links:read"),
    SPORT_QUEUE("sport:queue"),
    USER("user"),
    UNKNOWN("UNKNOWN"),
}

/** Where a registry client runs; wire values are lower case. Display: a newer value is [UNKNOWN]. */
@Serializable(with = ClientDeploymentSerializer::class)
enum class ClientDeployment(internal val wire: String) {
    SELF_HOSTED("self_hosted"),
    HOSTED("hosted"),
    UNKNOWN("UNKNOWN"),
}

/** The state of a connection's ITMO session as the client reports it. Display: a newer value is [UNKNOWN]. */
@Serializable(with = ItmoStatusSerializer::class)
enum class ItmoStatus(internal val wire: String) {
    OK("ok"),
    CLOSED("closed"),
    NONE("none"),
    UNKNOWN("UNKNOWN"),
}

internal object PairingKindSerializer :
    UnknownTolerantEnumSerializer<PairingKind>("PairingKind", PairingKind.entries, PairingKind.UNKNOWN)

internal object ConnectionClientSerializer :
    UnknownTolerantEnumSerializer<ConnectionClient>("ConnectionClient", ConnectionClient.entries, ConnectionClient.UNKNOWN) {
    override fun wireName(value: ConnectionClient): String = value.wire
}

internal object ConnectionScopeSerializer :
    UnknownTolerantEnumSerializer<ConnectionScope>("ConnectionScope", ConnectionScope.entries, ConnectionScope.UNKNOWN) {
    override fun wireName(value: ConnectionScope): String = value.wire
}

internal object ClientDeploymentSerializer :
    UnknownTolerantEnumSerializer<ClientDeployment>("ClientDeployment", ClientDeployment.entries, ClientDeployment.UNKNOWN) {
    override fun wireName(value: ClientDeployment): String = value.wire
}

internal object ItmoStatusSerializer :
    UnknownTolerantEnumSerializer<ItmoStatus>("ItmoStatus", ItmoStatus.entries, ItmoStatus.UNKNOWN) {
    override fun wireName(value: ItmoStatus): String = value.wire
}
