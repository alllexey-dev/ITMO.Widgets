package dev.alllexey.itmowidgets.feature.update.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class UpdateModuleTest {

    /** The repository and the wall clock are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theUpdateModuleResolvesWithTheBridgedTypes() {
        updateModule.verify(
            extraTypes = listOf(AppUpdateRepository::class, Clock::class, SavedStateHandle::class),
        )
    }
}
