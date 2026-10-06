package dev.alllexey.itmowidgets.feature.home.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class HomeModuleTest {

    /**
     * The other features' sources, the layout store, the opt-in and the wall clock are bridged from the app's Hilt
     * graph, the device status comes from the platform; the stores and the hint source resolve inside the module.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theHomeModuleResolvesWithTheBridgedTypes() {
        homeModule.verify(
            extraTypes = listOf(
                HomeCardSource::class,
                HomeLayoutPreferences::class,
                CustomServicesRepository::class,
                HomeHintStatus::class,
                Clock::class,
                SavedStateHandle::class,
            )
        )
    }
}
