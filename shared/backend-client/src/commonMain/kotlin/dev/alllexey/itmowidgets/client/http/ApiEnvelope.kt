package dev.alllexey.itmowidgets.client.http

import kotlinx.serialization.Serializable

/** Backend's `ApiResponse<T>` wrapper. Internal: no public API returns it. */
@Serializable
internal class ApiEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorDetails? = null,
) {
    /** `message` is not read: server text never reaches the app. */
    @Serializable
    class ErrorDetails(val code: String? = null)
}
