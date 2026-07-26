package dev.alllexey.itmowidgets.core.session

import android.util.Log
import com.google.gson.Gson
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CurrentUser(
    val isu: Int?,
    val name: String?,
    val pictureUrl: String?
)

interface CurrentUserProvider {

    suspend fun getCurrentUser(): CurrentUser?
}

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
    private val gson: Gson
) : CurrentUserProvider {

    override suspend fun getCurrentUser(): CurrentUser? = withContext(Dispatchers.IO) {
        val idToken = tokenStore.getIdToken() ?: return@withContext null
        decodeClaims(idToken)
    }

    private fun decodeClaims(idToken: String): CurrentUser? {
        return try {
            val payload = idToken.split(TOKEN_SEPARATOR).getOrNull(PAYLOAD_INDEX)
                ?: return null
            val json = Base64.getUrlDecoder()
                .decode(payload)
                .toString(Charsets.UTF_8)
            val claims = gson.fromJson(json, IdTokenClaims::class.java) ?: return null

            CurrentUser(
                isu = claims.isu,
                name = claims.name?.trim()?.takeIf(String::isNotEmpty),
                pictureUrl = claims.picture?.trim()?.takeIf(String::isNotEmpty)
            ).takeIf { it.isu != null || it.name != null }
        } catch (error: Exception) {
            // Never log the token or its payload.
            Log.w(TAG, "Unreadable id token payload: ${error.javaClass.simpleName}")
            null
        }
    }

    private data class IdTokenClaims(
        val isu: Int?,
        val name: String?,
        val picture: String?
    )

    private companion object {
        const val TAG = "CurrentUserProvider"
        const val TOKEN_SEPARATOR = '.'
        const val PAYLOAD_INDEX = 1
    }
}
