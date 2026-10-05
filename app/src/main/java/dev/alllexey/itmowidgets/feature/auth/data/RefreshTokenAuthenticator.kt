package dev.alllexey.itmowidgets.feature.auth.data

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.SessionTokens
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface RefreshTokenAuthenticator {

    suspend fun authenticate(refreshToken: String): SessionTokens
}

class DefaultRefreshTokenAuthenticator @Inject constructor(private val dispatchers: AppDispatchers) : RefreshTokenAuthenticator {

    override suspend fun authenticate(refreshToken: String): SessionTokens =
        withContext(dispatchers.io) {
            val candidate = MyItmo()
            candidate.storage.refreshToken = refreshToken
            candidate.storage.refreshExpiresAt = Long.MAX_VALUE
            candidate.forceRefreshTokens().toItmoIdTokenResponse().toSessionTokens()
        }
}

/**
 * ITMO.ID's (Keycloak) token endpoint response. Every field is optional on the wire: an incomplete response is
 * rejected by [toSessionTokens], not by the decoder. Not a data class: its `toString` must not print the tokens.
 */
@Serializable
internal class ItmoIdTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long = 0L,
    @SerialName("id_token") val idToken: String? = null
) {
    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun parse(json: String): ItmoIdTokenResponse = JSON.decodeFromString(serializer(), json)
    }
}

/** MyItmoApi 1.x still returns its own model from `forceRefreshTokens()` (until KM-10a1). */
internal fun api.myitmo.model.other.TokenResponse.toItmoIdTokenResponse() = ItmoIdTokenResponse(
    accessToken = accessToken,
    expiresIn = expiresIn,
    refreshToken = refreshToken,
    refreshExpiresIn = refreshExpiresIn,
    idToken = idToken
)

internal fun ItmoIdTokenResponse.toSessionTokens(): SessionTokens {
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
