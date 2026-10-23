package dev.alllexey.itmowidgets.feature.connections.presentation

import dev.alllexey.itmowidgets.core.connections.PairLink
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.connections.domain.AppInstall
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignIn
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInAttempt
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignInResult
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.Connection
import dev.alllexey.itmowidgets.feature.connections.domain.Devices
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoHealth
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogin
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLogins
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoSessionRef
import dev.alllexey.itmowidgets.feature.connections.domain.PairingApproval
import dev.alllexey.itmowidgets.feature.connections.domain.PairingKind
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import dev.alllexey.itmowidgets.feature.connections.domain.Scope
import dev.alllexey.itmowidgets.feature.connections.domain.ScopeGrant
import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** «Устройства» on Backend in memory; [calls] records what was asked, [failure] fails every call. */
internal class FakeDevicesRepository(initial: Devices? = null) : DevicesRepository {
    val state = MutableStateFlow(initial)
    override val devices: StateFlow<Devices?> = state
    var next: Devices? = initial
    var failure: AppError? = null
    val calls = mutableListOf<String>()

    override suspend fun refresh(): AppResult<Devices> {
        calls += "refresh"
        failure?.let { return AppResult.Failure(it) }
        val answer = next ?: return AppResult.Failure(AppError.Network)
        state.value = answer
        return AppResult.Success(answer)
    }

    override suspend fun updateScopes(id: Uuid, scopes: List<String>): AppResult<Connection> {
        calls += "scopes $id $scopes"
        failure?.let { return AppResult.Failure(it) }
        val current = state.value ?: return AppResult.Failure(AppError.NotFound)
        val updated = current.connections.first { it.id == id }.let { c ->
            c.copy(scopes = c.scopes.map { it.copy(granted = it.scope.id in scopes) })
        }
        state.value = current.copy(connections = current.connections.map { if (it.id == id) updated else it })
        return AppResult.Success(updated)
    }

    override suspend fun revoke(id: Uuid): AppResult<Unit> = record("revoke $id")

    override suspend fun itmoEnded(id: Uuid): AppResult<Unit> = record("ended $id")

    override suspend fun signOutInstall(instanceId: Uuid): AppResult<Unit> = record("signOut $instanceId")

    private fun record(call: String): AppResult<Unit> {
        calls += call
        return failure?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
    }
}

/** The ITMO.ID sessions the phone's cookie lists; [ended] records every `end`. */
internal class FakeItmoLogins(var answer: AppResult<ItmoLogins> = AppResult.Success(ItmoLogins.NoCookie)) : ItmoLoginsRepository {
    val ended = mutableListOf<List<String>>()

    override suspend fun list(): AppResult<ItmoLogins> = answer

    override suspend fun end(sids: List<String>): AppResult<Unit> {
        ended += sids
        return AppResult.Success(Unit)
    }
}

internal class FakePairingRepository(var opened: PairingResult<PairingRequest>) : PairingRepository {
    val approvals = mutableListOf<PairingApproval>()
    var approved: PairingResult<Uuid> = PairingResult.Success(Samples.agentId)

    override suspend fun open(link: PairLink): PairingResult<PairingRequest> = opened

    override suspend fun approve(request: PairingRequest, approval: PairingApproval): PairingResult<Uuid> {
        approvals += approval
        return approved
    }
}

/** Sign-ins whose callback is [CALLBACK]; each finishes with [result]. */
internal class FakeClientSignIn(var result: ClientSignInResult) : ClientSignIn {
    var begun = 0

    override suspend fun begin(request: PairingRequest): ClientSignInAttempt {
        begun++
        return object : ClientSignInAttempt {
            override val url = "https://id.itmo.ru/auth?attempt=$begun"
            override val userAgent = "UA ${request.name}"

            override fun isCallback(url: String) = url.startsWith(CALLBACK)

            override suspend fun finish(callbackUrl: String) = result
        }
    }

    companion object {
        const val CALLBACK = "https://my.itmo.ru/login/callback"
    }
}

internal object Samples {
    val agentId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000601")
    val webId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000603")
    val revokedId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000602")
    val phoneId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000701")
    val tabletId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000702")

    /** 12:00 Moscow on 7 October 2026, `FixedAcademicTime`'s default. */
    val now: Instant = Instant.parse("2026-10-07T09:00:00Z")

    fun scope(id: String, title: String) = Scope(id, title, "$title описание")

    fun agent(
        expiresAt: Instant = Instant.parse("2026-10-11T09:00:00Z"),
        health: ItmoHealth = ItmoHealth.OK,
        revokedAt: Instant? = null,
        id: Uuid = agentId,
        sid: String = "sid-agent",
    ) = Connection(
        id = id,
        client = ClientType.AGENT,
        name = "nas",
        version = "1.2",
        platform = "linux",
        scopes = listOf(
            ScopeGrant(scope("SPORT", "Запись на физкультуру"), true),
            ScopeGrant(scope("FRIENDS_READ", "Друзья"), false),
        ),
        createdAt = Instant.parse("2026-07-28T09:00:00Z"),
        approvedAt = Instant.parse("2026-07-28T09:00:00Z"),
        lastUsedAt = Instant.parse("2026-10-07T08:55:00Z"),
        itmo = ItmoSessionRef(sid, expiresAt),
        health = health,
        revokedAt = revokedAt,
    )

    fun web() = Connection(
        id = webId,
        client = ClientType.WEB,
        name = "Chrome, macOS",
        version = null,
        platform = null,
        scopes = listOf(ScopeGrant(scope("USER", "Всё"), true)),
        createdAt = Instant.parse("2026-10-02T09:00:00Z"),
        approvedAt = Instant.parse("2026-10-04T09:00:00Z"),
        lastUsedAt = null,
        itmo = null,
        health = ItmoHealth.NONE,
        revokedAt = null,
    )

    fun install(id: Uuid, current: Boolean, sid: String?) = AppInstall(
        instanceId = id,
        ios = !current,
        deviceName = if (current) "Google Pixel 8" else null,
        os = if (current) "36" else "26",
        version = "2.4.0",
        firstSeenAt = Instant.parse("2026-09-01T09:00:00Z"),
        lastSeenAt = Instant.parse("2026-10-06T18:40:00Z"),
        pushEnabled = true,
        sid = sid,
        current = current,
    )

    fun login(sid: String, label: String = "Chrome 141, Windows") = ItmoLogin(
        sid = sid,
        started = Instant.parse("2026-09-03T09:00:00Z"),
        lastAccess = Instant.parse("2026-10-07T07:00:00Z"),
        expires = Instant.parse("2026-12-02T09:00:00Z"),
        label = label,
        ipAddress = "92.100.4.12",
        mobile = false,
    )

    fun request(
        kind: PairingKind = PairingKind.CONNECT,
        client: ClientType = ClientType.AGENT,
        connectionId: Uuid? = null,
    ) = PairingRequest(
        challengeId = PairingVectors.CHALLENGE_ID,
        kind = kind,
        client = client,
        name = "nas",
        version = "1.2",
        platform = "linux",
        scopes = listOf(scope("SPORT", "Запись на физкультуру"), scope("FRIENDS_READ", "Друзья")),
        connectionId = connectionId,
    )
}
