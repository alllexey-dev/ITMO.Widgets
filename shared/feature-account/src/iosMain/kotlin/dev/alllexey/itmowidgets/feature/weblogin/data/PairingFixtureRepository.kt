package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.weblogin.PairLink
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.Connection
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingError
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingResult
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A synthetic agent and connections list for the iOS fixture (`WebLoginIosFixture`): [LINK] is an agent «nas» that
 * asked at 12:04 Moscow time and is approved at once; any other code is unknown. It never reaches Backend.
 */
class PairingFixtureRepository : PairingRepository {
    private val revoked = mutableSetOf<Uuid>()

    override suspend fun open(code: String, link: PairLink?): PairingResult<PairingRequest> = when {
        link != null && link.toUrl() == LINK -> PairingResult.Success(REQUEST)
        else -> PairingResult.Failure(PairingError.Failed(AppError.NotFound))
    }

    override suspend fun approve(
        request: PairingRequest,
        sealedBundle: String,
        scopes: Set<AgentScope>,
    ): PairingResult<Unit> = PairingResult.Success(Unit)

    override suspend fun reject(challengeId: Uuid): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun connections(): AppResult<List<Connection>> =
        AppResult.Success(CONNECTIONS.filterNot { it.id in revoked })

    override suspend fun revoke(id: Uuid): AppResult<Unit> = AppResult.Success(Unit).also { revoked += id }

    companion object {
        /** RFC 9180 A.2's recipient key and the secret 0..15, as the shared pairing vectors. */
        const val LINK = "https://dev.widgets.alllexey.dev/pair#v1.ABCD2345." +
            "QxDul9iMwfCIpVdsd6sM9cOseX89lROcbIS1QpxZZio.AAECAwQFBgcICQoLDA0ODw"

        private val REQUESTED_AT = Instant.parse("2026-09-01T09:04:00Z")

        private val REQUEST = PairingRequest(
            challengeId = Uuid.parse("0190f1a2-3b4c-7d5e-8f60-718293a4b5c6"),
            kind = PairingKind.AGENT,
            name = "nas",
            version = "0.1.0",
            userAgent = null,
            scopes = emptySet(),
            createdAt = REQUESTED_AT,
        )

        val CONNECTIONS = listOf(
            Connection(
                id = Uuid.parse("00000000-0000-4000-8000-000000000601"),
                kind = ConnectionKind.AGENT,
                name = "nas",
                userAgent = null,
                createdAt = Instant.parse("2026-09-01T09:05:00Z"),
                lastUsedAt = Instant.parse("2026-09-12T11:30:00Z"),
                itmoSession = true,
            ),
            Connection(
                id = Uuid.parse("00000000-0000-4000-8000-000000000501"),
                kind = ConnectionKind.WEB,
                name = null,
                userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/128.0.0.0 Safari/537.36",
                createdAt = Instant.parse("2026-08-30T18:00:00Z"),
                lastUsedAt = null,
                itmoSession = false,
            ),
        )
    }
}
