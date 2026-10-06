package dev.alllexey.itmowidgets.feature.weblogin.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class WebLoginModuleTest {

    /** The repository and the academic time are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theWebLoginModuleResolvesWithTheBridgedTypes() {
        webLoginModule.verify(
            extraTypes = listOf(WebLoginRepository::class, AcademicTimeProvider::class, SavedStateHandle::class),
        )
    }
}
