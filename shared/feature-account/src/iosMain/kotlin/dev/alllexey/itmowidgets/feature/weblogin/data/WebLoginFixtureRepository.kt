package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A synthetic browser for the iOS web sign-in fixture (`WebLoginIosFixture`): [CODE] is a Chrome on macOS that asked
 * at 12:04 Moscow time and is approved at once; any other code is unknown, as Backend answers an expired one. It
 * never reaches Backend.
 */
class WebLoginFixtureRepository : WebLoginRepository {

    override suspend fun preview(code: String): AppResult<WebLoginPreview> =
        if (code == CODE) AppResult.Success(PREVIEW) else AppResult.Failure(AppError.NotFound)

    override suspend fun approve(challengeId: Uuid): AppResult<Unit> =
        if (challengeId == PREVIEW.challengeId) AppResult.Success(Unit) else AppResult.Failure(AppError.NotFound)

    companion object {
        const val CODE = "ABCD2345"

        private val REQUESTED_AT = Instant.parse("2026-09-01T09:04:00Z")

        private val PREVIEW = WebLoginPreview(
            challengeId = Uuid.parse("00000000-0000-4000-8000-000000000001"),
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/128.0.0.0 Safari/537.36",
            createdAt = REQUESTED_AT,
            expiresAt = REQUESTED_AT + 2.minutes,
        )
    }
}
