package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult

interface BarsSessionRepository {
    /** The BARS sign-in page for one attempt; [state] comes back in the callback. */
    fun loginUrl(state: String): String

    /** Whether [url] is the BARS sign-in callback, whatever its state. */
    fun isCallback(url: String): Boolean

    suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit>
}
