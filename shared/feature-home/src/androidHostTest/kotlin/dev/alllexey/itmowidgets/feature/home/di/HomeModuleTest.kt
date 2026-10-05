package dev.alllexey.itmowidgets.feature.home.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class HomeModuleTest {

    /** The sources, the preferences, the hint store and the wall clock are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theHomeModuleResolvesWithTheBridgedTypes() {
        homeModule.verify(
            extraTypes = listOf(
                HomeCardSource::class,
                HomeCardPreferences::class,
                HomeHintStore::class,
                Clock::class,
                SavedStateHandle::class,
            )
        )
    }
}
