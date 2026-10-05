package dev.alllexey.itmowidgets.core.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * Whether the first-run flow has already been passed on this device.
 *
 * The flag belongs to the installation, not to the session: it survives sign-out,
 * so a second account on the same device does not repeat the flow. Only
 * `Повторить первоначальную настройку` in maintenance brings it back.
 */
interface OnboardingRepository {

    fun observeCompleted(): Flow<Boolean>

    suspend fun complete()

    suspend fun reset()
}
