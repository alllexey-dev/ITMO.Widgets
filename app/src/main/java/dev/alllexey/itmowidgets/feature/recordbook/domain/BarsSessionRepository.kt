package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult

interface BarsSessionRepository {
    suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit>
}
