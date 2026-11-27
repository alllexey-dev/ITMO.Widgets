package dev.alllexey.itmowidgets.feature.connections.data

import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The claims of an ITMO.ID JWT (an ID token, a `KEYCLOAK_IDENTITY` cookie), unverified: they tell sessions and
 * accounts apart on the phone, never authorize anything. Null for a token that is no JWT or lacks the claim; the token
 * is never logged.
 */
internal object IdTokenClaims {

    fun sid(idToken: String?): String? = string(idToken, "sid")

    /** The session of a token: `sid`, or `session_state` of older Keycloak tokens. */
    fun sessionId(token: String?): String? = sid(token) ?: string(token, "session_state")

    fun isu(idToken: String?): Int? = claim(idToken, "isu")?.intOrNull

    /** `exp` (seconds since the epoch). */
    fun expiresAt(token: String?): Instant? = claim(token, "exp")?.longOrNull?.let(Instant::fromEpochSeconds)

    private fun string(token: String?, name: String): String? =
        claim(token, name)?.takeIf { it.isString }?.content?.ifBlank { null }

    private fun claim(idToken: String?, name: String): JsonPrimitive? {
        val payload = idToken?.split('.')?.getOrNull(1) ?: return null
        return try {
            (JSON.parseToJsonElement(BASE64.decode(payload).decodeToString()) as? JsonObject)?.get(name) as? JsonPrimitive
        } catch (_: IllegalArgumentException) {
            null // Base64 and JSON failures, SerializationException included
        }
    }

    private val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val JSON = Json
}
