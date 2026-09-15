package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.result.AppResult

/** Whether the recordbook overlays BARS scores; off by default and reset on sign-out. */
interface BarsPreferenceRepository {
    suspend fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean): AppResult<Unit>
}
