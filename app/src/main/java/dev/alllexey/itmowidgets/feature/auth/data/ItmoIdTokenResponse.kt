package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmowidgets.core.storage.calculateTokenExpiration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * ITMO.ID's (Keycloak) token endpoint response, as the MyITMO callback page posts it after its own code exchange.
 * Every field is optional on the wire: an incomplete response is rejected by [toTokenSet], not by the decoder.
 * Not a data class: its `toString` must not print the tokens.
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

/** Lifetimes count from [nowMillis] and saturate like 2.2's, so the stored file is what 2.2 wrote. */
internal fun ItmoIdTokenResponse.toTokenSet(nowMillis: Long): TokenSet {
    val access = accessToken?.trim().orEmpty()
    val refresh = refreshToken?.trim().orEmpty()
    val identity = idToken?.trim().orEmpty()
    require(access.isNotEmpty() && refresh.isNotEmpty() && identity.isNotEmpty()) {
        "ITMO.ID returned an incomplete token response"
    }
    require(expiresIn > 0L && refreshExpiresIn > 0L) {
        "ITMO.ID returned invalid token expiration"
    }
    return TokenSet(
        accessToken = access,
        accessExpiresAt = Instant.fromEpochMilliseconds(calculateTokenExpiration(nowMillis, expiresIn)),
        refreshToken = refresh,
        refreshExpiresAt = Instant.fromEpochMilliseconds(calculateTokenExpiration(nowMillis, refreshExpiresIn)),
        idToken = identity
    )
}
