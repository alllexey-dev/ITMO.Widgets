package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.text.personName
import kotlin.io.encoding.Base64
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads the signed-in user from the locally stored ITMO.ID token.
 *
 * The same claims are what Backend stores about the user, so decoding them on the
 * device keeps the profile available offline and while custom services are off.
 * The token is not verified here: it is our own token and the result is only used
 * for display, never for authorization.
 */
class IdTokenCurrentUserProvider(
    private val tokenStore: SessionTokenStore,
    private val dispatchers: AppDispatchers,
    private val log: AppLog
) : CurrentUserProvider {

    override suspend fun getCurrentUser(): CurrentUser? = withContext(dispatchers.io) {
        val idToken = tokenStore.getIdToken() ?: return@withContext null
        decodeClaims(idToken)
    }

    private fun decodeClaims(idToken: String): CurrentUser? {
        return try {
            val payload = idToken.split(TOKEN_SEPARATOR).getOrNull(PAYLOAD_INDEX)
                ?: return null
            val json = PAYLOAD_BASE64.decode(payload).decodeToString()
            val claims = CLAIMS_JSON.decodeFromString<IdTokenClaims>(json)

            CurrentUser(
                isu = claims.isu,
                name = personName(claims.familyName, claims.givenName) ?: claims.name?.trim()?.takeIf(String::isNotEmpty),
                pictureUrl = claims.picture?.trim()?.takeIf(String::isNotEmpty),
                familyName = claims.familyName?.trim()?.takeIf(String::isNotEmpty),
                givenName = claims.givenName?.trim()?.takeIf(String::isNotEmpty),
                middleName = claims.middleName?.trim()?.takeIf(String::isNotEmpty),
            ).takeIf { it.isu != null || it.name != null }
        } catch (error: Exception) {
            // Never log the token or its payload.
            log.warn(TAG, "Unreadable id token payload: ${error::class.simpleName}")
            null
        }
    }

    @Serializable
    private data class IdTokenClaims(
        val isu: Int? = null,
        val name: String? = null,
        val picture: String? = null,
        @SerialName("family_name") val familyName: String? = null,
        @SerialName("given_name") val givenName: String? = null,
        @SerialName("middle_name") val middleName: String? = null,
    )

    private companion object {
        const val TAG = "CurrentUserProvider"
        const val TOKEN_SEPARATOR = '.'
        const val PAYLOAD_INDEX = 1

        /** JWT segments are unpadded; padding stays accepted, as the JDK URL decoder does. */
        val PAYLOAD_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        /** The token carries many more claims than these. */
        val CLAIMS_JSON = Json { ignoreUnknownKeys = true }
    }
}
