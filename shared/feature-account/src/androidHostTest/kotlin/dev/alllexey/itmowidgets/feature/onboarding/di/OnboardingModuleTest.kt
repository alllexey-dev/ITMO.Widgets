package dev.alllexey.itmowidgets.feature.onboarding.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
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

    /** The flow reads the flag of [onboardingDataModule]; the session, opt-in and widget settings are bridged. */
    @Test
    fun theOnboardingModulesResolveWithTheBridgedTypes() {
        module { includes(onboardingDataModule, onboardingModule) }.verify(
            extraTypes = listOf(
                UtilityStorage::class,
                SessionRepository::class,
                CustomServicesRepository::class,
                WidgetAppearanceRepository::class,
                CustomSpoilerRepository::class,
                SavedStateHandle::class,
            ),
        )
    }
}
