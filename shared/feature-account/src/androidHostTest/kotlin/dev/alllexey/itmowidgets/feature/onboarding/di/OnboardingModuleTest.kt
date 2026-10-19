package dev.alllexey.itmowidgets.feature.onboarding.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class OnboardingModuleTest {

    /** The first-run flag resolves over the platform's `UtilityStorage`. */
    @Test
    fun theOnboardingDataModuleResolvesWithThePlatformStorage() {
        onboardingDataModule.verify(extraTypes = listOf(UtilityStorage::class))
    }

    /**
     * The flow reads the flag of [onboardingDataModule]; the session, the opt-in, the calendar sync and the
     * capabilities come from the platform.
     */
    @Test
    fun theOnboardingModulesResolveWithThePlatformTypes() {
        module { includes(onboardingDataModule, onboardingModule) }.verify(
            extraTypes = listOf(
                UtilityStorage::class,
                SessionRepository::class,
                CustomServicesRepository::class,
                CalendarSync::class,
                PlatformCapabilities::class,
                SavedStateHandle::class,
            ),
        )
    }
}
