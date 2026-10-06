package dev.alllexey.itmowidgets.feature.resources.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class ResourcesKoinModuleTest {

    /** The repository is bridged from the app's Hilt graph, the handle comes from the host Fragment. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theResourcesModuleResolvesWithTheBridgedTypes() {
        resourcesModule.verify(extraTypes = listOf(SubjectLinksRepository::class, SavedStateHandle::class))
    }
}
