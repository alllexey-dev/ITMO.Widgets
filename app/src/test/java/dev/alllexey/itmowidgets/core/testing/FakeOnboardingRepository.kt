package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/** The first-run flag of one test; a null [completed] is a flag not read yet, so observers wait. */
class FakeOnboardingRepository(completed: Boolean? = false) : OnboardingRepository {
    val completed = MutableStateFlow(completed)
    var resetCount = 0
        private set

    override fun observeCompleted(): Flow<Boolean> = completed.filterNotNull()

    override suspend fun complete() {
        completed.value = true
    }

    override suspend fun reset() {
        resetCount += 1
        completed.value = false
    }
}
