package dev.alllexey.itmowidgets.feature.resources.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.links.SubjectLinksApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class ResourcesKoinModuleTest {

    /** The data resolves inside the module; only the platform's core types are bridged, the handle is the host's. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theResourcesModuleResolvesWithTheBridgedTypes() {
        resourcesModule.verify(
            extraTypes = listOf(
                BackendGate::class,
                SubjectLinksApi::class,
                AppDirectories::class,
                Clock::class,
                DemoMode::class,
                AppDispatchers::class,
                SavedStateHandle::class,
            )
        )
    }
}
