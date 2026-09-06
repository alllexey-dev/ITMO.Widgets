package dev.alllexey.itmowidgets.feature.auth.data

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.session.SessionTokens
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface RefreshTokenAuthenticator {

    suspend fun authenticate(refreshToken: String): SessionTokens
}

class DefaultRefreshTokenAuthenticator @Inject constructor() : RefreshTokenAuthenticator {

    override suspend fun authenticate(refreshToken: String): SessionTokens =
        withContext(Dispatchers.IO) {
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

internal fun Throwable.isCausedByNetworkFailure(): Boolean {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is IOException) return true
        current = current.cause
    }
    return false
}
