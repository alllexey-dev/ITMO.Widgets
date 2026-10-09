package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.ClientVersion
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlin.coroutines.cancellation.CancellationException

/**
 * The HTTP core every `Ktor<Area>Api` calls. It owns the status-first decoding: a non-2xx status becomes a typed
 * [BackendException] before the body is decoded, so an HTML 502 or an empty 401 never crashes the decoder. Every
 * request carries [version] as [ClientVersion.HEADER] when the holder gave one.
 */
internal class BackendHttp(
    baseUrl: String,
    private val tokens: AccessTokenSource,
    engine: HttpClientEngine,
    private val version: ClientVersion?,
) {
    private val origin = URLBuilder(baseUrl).build()

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(BackendJson) }
    }

    /** Calls [route] and returns its non-null `data`; a missing or `null` `data` is a [BackendException.Contract]. */
    suspend fun <T : Any> call(route: BackendRoute, result: KSerializer<T>): T =
        decodeEnvelope(send(route), result).data ?: throw contract("Backend envelope has no data")

    /** Calls [route] for its effect; whatever `data` holds is ignored. */
    suspend fun callUnit(route: BackendRoute) {
        decodeEnvelope(send(route), JsonElement.serializer())
    }

    /** The 2xx body text; any other status is thrown as its [BackendException]. */
    private suspend fun send(route: BackendRoute): String {
        val token = transport { tokens.accessToken() }?.trim()?.takeIf(String::isNotEmpty)
        val response = transport { execute(route, token) }
        val text = transport { response.bodyAsText() }
        if (!response.status.isSuccess()) throw statusException(response.status.value, errorCode(text))
        return text
    }

    private suspend fun execute(route: BackendRoute, token: String?): HttpResponse =
        try {
            client.request {
                method = route.method
                url {
                    takeFrom(origin)
                    encodedPathSegments = emptyList()
                    appendPathSegments(listOf("") + route.segments, encodeSlash = true)
                    route.query.forEach { (name, value) -> if (value != null) parameters.append(name, value) }
                }
                if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
                if (version != null) header(ClientVersion.HEADER, version.headerValue)
                route.body?.let {
                    contentType(ContentType.Application.Json)
                    setBody(it.value, it.type)
                }
            }
        } catch (error: SerializationException) {
            // The request body could not be encoded (an UNKNOWN display value): a client bug, not a transport one.
            throw BackendException.Contract(error)
        }

    private fun <T> decodeEnvelope(text: String, data: KSerializer<T>): ApiEnvelope<T> {
        val envelope = try {
            BackendJson.decodeFromString(ApiEnvelope.serializer(data), text)
        } catch (error: IllegalArgumentException) {
            // A SerializationException (shape, missing field, unknown strict enum) or a model `init` invariant.
            throw BackendException.Contract(error)
        }
        if (!envelope.success) throw contract("Backend envelope has success=false")
        return envelope
    }

    private fun errorCode(text: String): String? =
        try {
            BackendJson.decodeFromString(ApiEnvelope.serializer(JsonElement.serializer()), text).error?.code
        } catch (_: IllegalArgumentException) {
            null // empty, not JSON or not Backend's envelope
        }

    private fun statusException(status: Int, code: String?): BackendException = when (status) {
        401 -> BackendException.Unauthorized()
        403 -> BackendException.Forbidden(code)
        404 -> BackendException.NotFound(code)
        else -> BackendException.Http(status, code)
    }

    private fun contract(reason: String) = BackendException.Contract(IllegalStateException(reason))

    /** Runs [block]; a failure other than cancellation or a typed [BackendException] means there was no answer. */
    private inline fun <T> transport(block: () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: BackendException) {
            throw error
        } catch (error: Exception) {
            throw BackendException.Transport(error)
        }
}
