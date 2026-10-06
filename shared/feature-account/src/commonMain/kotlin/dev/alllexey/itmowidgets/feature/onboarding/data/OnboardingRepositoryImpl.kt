package dev.alllexey.itmowidgets.feature.onboarding.data

import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlinx.coroutines.flow.Flow

class OnboardingRepositoryImpl(
    private val storage: UtilityStorage
) : OnboardingRepository {

    override fun observeCompleted(): Flow<Boolean> = storage.observeOnboardingCompleted()

    override suspend fun complete() {
        storage.setOnboardingCompleted(true)
    }

    override suspend fun reset() {
        storage.setOnboardingCompleted(false)
    }
}
