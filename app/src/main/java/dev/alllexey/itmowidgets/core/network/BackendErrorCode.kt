package dev.alllexey.itmowidgets.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.util.WeakHashMap

private val codes = WeakHashMap<HttpException, String?>()

/** ResponseBody is single-use; cache the parsed code without retaining errors or exposing server messages. */
internal fun HttpException.backendErrorCode(): String? = synchronized(codes) {
    if (codes.containsKey(this)) return@synchronized codes[this]
    val code = runCatching { response()?.errorBody()?.string()?.let(::errorCodeOf) }.getOrNull()
    codes[this] = code
    code
}

/** `error.code` of Backend's `{"success": false, "error": {"message": ..., "code": ...}}` envelope. */
internal fun errorCodeOf(body: String): String? {
    val error = (Json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject ?: return null
    val code = error["code"] as? JsonPrimitive ?: return null
    return code.takeUnless { it is JsonNull }?.content
}
