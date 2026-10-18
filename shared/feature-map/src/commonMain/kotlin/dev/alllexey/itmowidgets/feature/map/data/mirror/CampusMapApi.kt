package dev.alllexey.itmowidgets.feature.map.data.mirror

import dev.alllexey.itmowidgets.core.demo.DemoMode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CancellationException
import io.ktor.http.isSuccess

/** Where the mirror is: `https://map.alllexey.dev/app/v1/`, or a local mirror in development (docs/features/map.md). */
data class CampusMapEndpoint(val url: String)

/** A file of the mirror could not be read: [status] is the HTTP code, null for a network failure. */
internal class MirrorException(val status: Int?) : Exception("Campus map mirror answered ${status ?: "nothing"}")

/**
 * Reads the public campus-map mirror: the manifest and its content-addressed files. Requests carry no account, token
 * or session, so the demo session reads the same files as everyone ([demo] is not consulted: there is nothing of the
 * user to keep off the network, and the demo lessons are in real buildings).
 */
class CampusMapApi internal constructor(
    private val client: HttpClient,
    endpoint: CampusMapEndpoint,
    @Suppress("unused") private val demo: DemoMode,
) {
    constructor(endpoint: CampusMapEndpoint, demo: DemoMode) : this(campusMapHttpClient(campusMapEngine()), endpoint, demo)

    private val base = endpoint.url.let { if (it.endsWith("/")) it else "$it/" }

    internal suspend fun manifest(): String = get(MANIFEST).decodeToString()

    internal suspend fun file(path: String): ByteArray = get(path)

    /** The address of [path] relative to the manifest, kept verbatim: the glyphs template has MapLibre's `{}` tokens. */
    fun resolve(path: String): String = if (ABSOLUTE.containsMatchIn(path)) path else base + path.removePrefix("/")

    private suspend fun get(path: String): ByteArray {
        val response = try {
            client.get(resolve(path))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw MirrorException(null)
        }
        if (!response.status.isSuccess()) throw MirrorException(response.status.value)
        return response.bodyAsBytes()
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        val ABSOLUTE = Regex("^[a-z]+://")
    }
}
