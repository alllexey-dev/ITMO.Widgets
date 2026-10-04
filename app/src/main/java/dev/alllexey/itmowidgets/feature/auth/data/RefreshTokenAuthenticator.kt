package dev.alllexey.itmowidgets.feature.auth.data

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionTokens
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface RefreshTokenAuthenticator {

    suspend fun authenticate(refreshToken: String): SessionTokens
}

class DefaultRefreshTokenAuthenticator @Inject constructor(private val dispatchers: AppDispatchers) : RefreshTokenAuthenticator {

    override suspend fun authenticate(refreshToken: String): SessionTokens =
        withContext(dispatchers.io) {
            val candidate = MyItmo()
            candidate.storage.refreshToken = refreshToken
            candidate.storage.refreshExpiresAt = Long.MAX_VALUE
            candidate.forceRefreshTokens().toSessionTokens()
        }
}

internal fun api.myitmo.model.other.TokenResponse.toSessionTokens(): SessionTokens {
    val access = accessToken?.trim().orEmpty()
    val refresh = refreshToken?.trim().orEmpty()
    val identity = idToken?.trim().orEmpty()
    require(access.isNotEmpty() && refresh.isNotEmpty() && identity.isNotEmpty()) {
        "ITMO.ID returned an incomplete token response"
    }
    require(expiresIn > 0L && refreshExpiresIn > 0L) {
        "ITMO.ID returned invalid token expiration"
    }
    return SessionTokens(
        accessToken = access,
        accessExpiresInSeconds = expiresIn,
        refreshToken = refresh,
        refreshExpiresInSeconds = refreshExpiresIn,
        idToken = identity
    )
}
