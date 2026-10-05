package dev.alllexey.itmowidgets.client.error

/**
 * Every failure of a Backend call. [code] is Backend's `error.code` (`restricted`, `permission_denied`,
 * `access_denied`, `csrf`, `not_found`, `invalid_request`, ...) or `null` when the error body is empty, not JSON or
 * not Backend's envelope. The server `message` is never kept: user-visible text belongs to the app.
 */
sealed class BackendException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** HTTP 401: missing or rejected credentials. Never retried with a refreshed token by the client. */
    class Unauthorized : BackendException("HTTP 401")

    /** HTTP 403, distinct from 401; [code] tells a moderation restriction from a hidden or denied resource. */
    class Forbidden(val code: String?) : BackendException("HTTP 403 ${code.orEmpty()}".trimEnd())

    /** HTTP 404. */
    class NotFound(val code: String?) : BackendException("HTTP 404 ${code.orEmpty()}".trimEnd())

    /** Any other non-2xx [status]. */
    class Http(val status: Int, val code: String?) : BackendException("HTTP $status ${code.orEmpty()}".trimEnd())

    /** No response: the token source, the connection or reading the body failed. */
    class Transport(cause: Throwable) : BackendException("Backend unreachable", cause)

    /**
     * A 2xx answer that breaks the contract: the body fails decoding or a model invariant, the envelope says
     * `success=false`, or `data` is missing for a call that returns a value.
     */
    class Contract(cause: Throwable) : BackendException("Backend answer breaks the contract", cause)
}
