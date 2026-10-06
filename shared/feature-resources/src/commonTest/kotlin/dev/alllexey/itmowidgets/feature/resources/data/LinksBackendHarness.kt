package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Core 2.0 client over one MockEngine with the stored access token, as the app builds it from the MyItmoApi
 * 2.x session. [requests] records every request; MockEngine may answer concurrent calls on several threads, so
 * recording takes a lock.
 */
internal class LinksBackendHarness(
    private val backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val lock = Mutex()
    private val recorded = mutableListOf<HttpRequestData>()
    val requests: List<HttpRequestData> get() = recorded.toList()

    private val engine = MockEngine { request ->
        lock.withLock { recorded += request }
        backend(request)
    }

    val client = BackendClient(BACKEND_URL, AccessTokenSource { STORED_BACKEND_ACCESS }, engine)

    companion object {
        const val BACKEND_URL = "https://backend.test"
        const val STORED_BACKEND_ACCESS = "stored-access"

        /** Backend's error envelope (`GlobalExceptionHandler`) with a synthetic message. */
        fun errorEnvelope(code: String): String =
            """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""
    }
}
