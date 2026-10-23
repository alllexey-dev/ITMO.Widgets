package dev.alllexey.itmowidgets.feature.connections.data

import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The `sid` and `isu` claims of an ITMO.ID token, unverified: they tell sessions and accounts apart on the phone,
 * never authorize anything. Null for a token that is no JWT or lacks the claim; the token is never logged.
 */
internal object IdTokenClaims {

    fun sid(idToken: String?): String? = claim(idToken, "sid")?.takeIf { it.isString }?.content?.ifBlank { null }

    fun isu(idToken: String?): Int? = claim(idToken, "isu")?.intOrNull

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
