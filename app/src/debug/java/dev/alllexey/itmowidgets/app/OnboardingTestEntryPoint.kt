package dev.alllexey.itmowidgets.app

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository

/**
 * Test-only access to the first-run flag in the actual graph.
 *
 * The flag lives in the application's DataStore; a second instance over the same
 * file would be rejected, so a routing test reaches the real one through Hilt.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnboardingTestEntryPoint {
    fun onboarding(): OnboardingRepository
}
