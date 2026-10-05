package dev.alllexey.itmowidgets.core.weblogin

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** A browser waiting for this account to approve its sign-in to the web version. */
data class WebLoginPreview(
    val challengeId: Uuid,
    /** The browser's User-Agent as Backend recorded it; null when the browser sent none. */
    val userAgent: String?,
    val createdAt: Instant,
    val expiresAt: Instant,
)

/**
 * Phone-approved sign-in to the web version. Both calls go to Backend, so both fail with
 * [dev.alllexey.itmowidgets.core.result.AppError.CustomServicesDisabled] without the opt-in and with
 * [dev.alllexey.itmowidgets.core.result.AppError.NotFound] for a code that is unknown, used or expired.
 */
interface WebLoginRepository {

    /** [code] is already normalized: eight characters of the sign-in alphabet. */
    suspend fun preview(code: String): AppResult<WebLoginPreview>

    suspend fun approve(challengeId: Uuid): AppResult<Unit>
}
