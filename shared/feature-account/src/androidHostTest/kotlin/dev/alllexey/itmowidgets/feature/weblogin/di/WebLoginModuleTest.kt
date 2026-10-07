package dev.alllexey.itmowidgets.feature.weblogin.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class WebLoginModuleTest {

    /** Core 2.0's users area, the gate, the demo switch, the dispatchers and the academic time are core bindings. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theWebLoginModulesResolveWithTheBridgedTypes() {
        module { includes(webLoginDataModule, webLoginModule) }.verify(
            extraTypes = listOf(
                UsersApi::class,
                BackendGate::class,
                DemoMode::class,
                AppDispatchers::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
            ),
        )
    }
}
