package dev.alllexey.itmowidgets.client.moderation

import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException

/** The author's own moderation notices (Backend's `notifications.md`, Moderation notices). */
interface ModerationNoticesApi {

    /**
     * `GET /api/users/me/moderation-notices`: the caller's notices that became due in the last 30 days, the newest
     * decision first, at most 50; a decision is due 11 minutes after it was taken (its undo window and a minute).
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun notices(): List<ModerationNotice>
}
