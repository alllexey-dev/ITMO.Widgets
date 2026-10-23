package dev.alllexey.itmowidgets.feature.connections.domain

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow

/** A connected client's type; [OTHER] is one this version does not know. */
enum class ClientType { AGENT, MCP, WEB, OTHER }

/** The client's own report about its ITMO session. */
enum class ItmoHealth { OK, CLOSED, NONE }

/** A Widgets scope with Backend's texts; [id] is what a choice sends back, null for one this version cannot send. */
data class Scope(val id: String?, val title: String, val description: String)

data class ScopeGrant(val scope: Scope, val granted: Boolean)

/** A connection's ITMO.ID session as Backend keeps it; [toString] hides the `sid`. */
data class ItmoSessionRef(val sid: String, val expiresAt: Instant) {
    override fun toString(): String = "ItmoSessionRef(sid=<redacted>, expiresAt=$expiresAt)"
}

/** A connected client. [revokedAt] is set on a revoked one whose ITMO session the phone has not ended yet. */
data class Connection(
    val id: Uuid,
    val client: ClientType,
    val name: String,
    val version: String?,
    val platform: String?,
    val scopes: List<ScopeGrant>,
    val createdAt: Instant,
    val approvedAt: Instant,
    val lastUsedAt: Instant?,
    val itmo: ItmoSessionRef?,
    val health: ItmoHealth,
    val revokedAt: Instant?,
)

/** One app install of the user; [os] is the Android API level or the iOS major version. */
data class AppInstall(
    val instanceId: Uuid,
    val ios: Boolean,
    val deviceName: String?,
    val os: String?,
    val version: String,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val pushEnabled: Boolean,
    val sid: String?,
    val current: Boolean,
)

data class Devices(val connections: List<Connection>, val installs: List<AppInstall>) {
    /** «Подключённые»: revoked rows wait only for the phone to end their ITMO session. */
    val liveConnections: List<Connection> get() = connections.filter { it.revokedAt == null }

    /**
     * «Мои устройства»: other installs with a device registration, or whose ITMO session is among [openSids] (null
     * while ITMO.ID was not listed). The install id is new after every sign-in, so each earlier sign-in of a phone
     * leaves a nameless row behind.
     */
    fun otherInstalls(openSids: Set<String>?): List<AppInstall> = installs.filter {
        !it.current && (it.deviceName != null || (openSids != null && it.sid != null && it.sid in openSids))
    }

    /** What Профиль counts: exactly the rows of «Мои устройства» and «Подключённые», this phone not included. */
    fun shownCount(openSids: Set<String>?): Int = otherInstalls(openSids).size + liveConnections.size
}

/**
 * One ITMO.ID session of the account as id.itmo.ru/activity shows it: [label] is ITMO's own («Chrome 141, Windows»).
 * Holds an IP address, so it never reaches a log or Backend.
 */
data class ItmoLogin(
    val sid: String,
    val started: Instant,
    val lastAccess: Instant,
    val expires: Instant,
    val label: String,
    val ipAddress: String?,
    val mobile: Boolean,
) {
    override fun toString(): String = "ItmoLogin(redacted)"
}

/**
 * «Устройства» on Backend. [devices] is the last answer in memory (null before one); every call fails with
 * `CustomServicesDisabled` without the opt-in and `DemoUnavailable` in the demo session.
 */
interface DevicesRepository {
    val devices: StateFlow<Devices?>

    suspend fun refresh(): AppResult<Devices>

    suspend fun updateScopes(id: Uuid, scopes: List<String>): AppResult<Connection>

    suspend fun revoke(id: Uuid): AppResult<Unit>

    suspend fun itmoEnded(id: Uuid): AppResult<Unit>

    suspend fun signOutInstall(instanceId: Uuid): AppResult<Unit>
}

/** What the phone's own ITMO.ID cookie sees. */
sealed interface ItmoLogins {
    /** The phone has no usable SSO cookie (signed in with a pasted token, or the cookie is gone). */
    data object NoCookie : ItmoLogins

    /** Every session of the account; [ownSid] is this phone's. */
    data class Listed(val sessions: List<ItmoLogin>, val ownSid: String?) : ItmoLogins
}

/** The account's ITMO.ID sessions through the phone's own cookie; refused in the demo session. */
interface ItmoLoginsRepository {
    /** The last successful listing, kept in memory so Профиль counts what «Устройства» showed. */
    val lastListed: StateFlow<ItmoLogins.Listed?>

    suspend fun list(): AppResult<ItmoLogins>

    /** Ends [sids]; an already ended one is fine. `Unauthorized` when the cookie is gone. */
    suspend fun end(sids: List<String>): AppResult<Unit>
}

/** Why a client's ITMO sign-in needs the user: [Closed] (closed, ended or missing) or [Ending] within 7 days. */
sealed interface SignInAlert {
    data object Closed : SignInAlert

    data class Ending(val at: Instant) : SignInAlert
}

/** The reminder comes 7 days before a client's ITMO sign-in ends (UX spec U2). */
val SIGN_IN_REMINDER: Duration = 7.days

/** An agent or MCP server that holds its own ITMO session. */
val Connection.holdsItmo: Boolean get() = client == ClientType.AGENT || client == ClientType.MCP

/** Null for the web, a revoked client or a sign-in that is fine at [now]. */
fun Connection.signInAlert(now: Instant): SignInAlert? {
    if (!holdsItmo || revokedAt != null) return null
    val ends = itmo?.expiresAt
    if (health == ItmoHealth.CLOSED || ends == null || ends <= now) return SignInAlert.Closed
    return if (ends - now <= SIGN_IN_REMINDER) SignInAlert.Ending(ends) else null
}
