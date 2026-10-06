package dev.alllexey.itmowidgets.feature.onboarding.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class OnboardingModuleTest {

    /** The flag, the session, the opt-in and the widget settings are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theOnboardingModuleResolvesWithTheBridgedTypes() {
        onboardingModule.verify(
            extraTypes = listOf(
                OnboardingRepository::class,
                SessionRepository::class,
                CustomServicesRepository::class,
                WidgetAppearanceRepository::class,
                CustomSpoilerRepository::class,
                SavedStateHandle::class,
            ),
        )
    }
}
