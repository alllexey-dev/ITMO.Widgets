package dev.alllexey.itmowidgets.client.http

import io.ktor.http.HttpMethod
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo

/**
 * One Backend call. [segments] are the absolute path's raw segments, each percent-encoded on its own like a
 * Retrofit `@Path` (a `/` inside a segment stays inside it). [query] pairs with a `null` value are omitted.
 */
internal class BackendRoute(
    val method: HttpMethod,
    val segments: List<String>,
    val query: List<Pair<String, String?>> = emptyList(),
    val body: BackendBody? = null,
)

/** A JSON request body, encoded by [dev.alllexey.itmowidgets.client.json.BackendJson]; nulls are omitted. */
internal class BackendBody(val value: Any, val type: TypeInfo)

internal inline fun <reified T : Any> jsonBody(value: T): BackendBody = BackendBody(value, typeInfo<T>())
