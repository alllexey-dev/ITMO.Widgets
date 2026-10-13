package dev.alllexey.itmowidgets.feature.home.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import kotlin.test.Test
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class HomeModuleTest {

    /**
     * The other features' sources, the layout store, the opt-in and the wall clock come from the platform's Koin
     * modules, as does the device status; the stores and the hint source resolve inside the module.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theHomeModuleResolvesWithThePlatformTypes() {
        homeModule.verify(
            extraTypes = listOf(
                HomeCardSource::class,
                HomeLayoutPreferences::class,
                CustomServicesRepository::class,
                HomeHintStatus::class,
                Clock::class,
                SavedStateHandle::class,
                AcademicTimeProvider::class,
            )
        )
    }
}
