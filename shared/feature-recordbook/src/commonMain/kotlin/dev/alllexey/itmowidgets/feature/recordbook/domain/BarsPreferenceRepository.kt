package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult

/**
 * Whether the recordbook lays BARS over My ITMO (BARS connected in the recordbook), and whether its quiet offer to
 * connect was hidden. Both are off by default and reset on sign-out.
 */
interface BarsPreferenceRepository {
    suspend fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean): AppResult<Unit>
    suspend fun isOfferHidden(): Boolean
    suspend fun hideOffer(): AppResult<Unit>
}
