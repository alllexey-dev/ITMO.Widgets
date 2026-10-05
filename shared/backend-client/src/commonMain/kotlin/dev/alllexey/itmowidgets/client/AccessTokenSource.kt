package dev.alllexey.itmowidgets.client

/**
 * The ITMO.ID access token for Backend, asked once per request.
 *
 * `null` or a blank token sends the request without an `Authorization` header (public routes and the signed-out
 * state). The client never refreshes on 401: a source that refreshes does so before it returns. A source that throws
 * sends no request; the call fails with [dev.alllexey.itmowidgets.client.error.BackendException.Transport] keeping
 * the thrown exception as the cause, and a `CancellationException` passes through unchanged.
 */
fun interface AccessTokenSource {
    @Throws(Exception::class)
    suspend fun accessToken(): String?
}
