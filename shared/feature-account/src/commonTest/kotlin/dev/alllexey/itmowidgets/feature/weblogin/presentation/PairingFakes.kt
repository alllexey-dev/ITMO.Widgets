package dev.alllexey.itmowidgets.feature.weblogin.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.Connection
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffSession
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffToken
import dev.alllexey.itmowidgets.feature.weblogin.domain.HpkeSender
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingError
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingResult
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred

/** Opens what [opens] holds by code (every other code is unknown) and records each call. */
internal class FakePairingRepository : PairingRepository {
    val opens = mutableMapOf<String, PairingResult<PairingRequest>>()
    var approval: PairingResult<Unit> = PairingResult.Success(Unit)
    var connections: AppResult<List<Connection>> = AppResult.Success(emptyList())
    var pendingConnections: CompletableDeferred<AppResult<List<Connection>>>? = null
    var revocation: AppResult<Unit> = AppResult.Success(Unit)
    val calls = mutableListOf<String>()

    override suspend fun open(code: String, link: PairLink?): PairingResult<PairingRequest> {
        calls += "open:$code:${if (link == null) "typed" else "link"}"
        return opens[code] ?: PairingResult.Failure(PairingError.Failed(AppError.NotFound))
    }

    override suspend fun approve(
        request: PairingRequest,
        sealedBundle: String,
        scopes: Set<AgentScope>,
    ): PairingResult<Unit> {
        calls += "approve:${request.challengeId}:${scopes.sorted()}"
        return approval
    }

    override suspend fun reject(challengeId: Uuid): AppResult<Unit> {
        calls += "reject:$challengeId"
        return AppResult.Success(Unit)
    }

    override suspend fun connections(): AppResult<List<Connection>> {
        calls += "connections"
        return pendingConnections?.await() ?: connections
    }

    override suspend fun revoke(id: Uuid): AppResult<Unit> {
        calls += "revoke:$id"
        return revocation
    }
}

internal class FakeSession(
    var result: AppResult<HandoffToken> =
        AppResult.Success(HandoffToken("test-refresh-token", Instant.parse("2026-11-11T09:00:00Z"))),
) : HandoffSession {
    var rotations = 0

    override suspend fun rotate(): AppResult<HandoffToken> = result.also { rotations += 1 }
}

/** Seals to nothing but the right length, and keeps the plaintext. */
internal class RecordingHpke : HpkeSender {
    var plaintext: ByteArray? = null

    override fun seal(recipientPublicKey: ByteArray, info: ByteArray, plaintext: ByteArray): ByteArray {
        this.plaintext = plaintext
        return ByteArray(48 + plaintext.size)
    }
}

internal object SignedIn : CurrentUserProvider {
    override suspend fun getCurrentUser() = CurrentUser(isu = 100001, name = null, pictureUrl = null)
}
