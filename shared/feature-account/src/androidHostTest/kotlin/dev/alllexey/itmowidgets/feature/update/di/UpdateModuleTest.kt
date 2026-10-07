package dev.alllexey.itmowidgets.feature.update.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class UpdateModuleTest {

    /**
     * The installed version, its platform and Core 2.0's app area come from the platform (`AccountUpdateBridge` on
     * Android), the gate, the demo switch, the reminder store, the wall clock, diagnostics and dispatchers from the
     * core bindings; the repository resolves inside the module.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theUpdateModuleResolvesWithTheBridgedTypes() {
        updateModule.verify(
            extraTypes = listOf(
                AppApi::class,
                AppVersionName::class,
                DevicePlatform::class,
                BackendGate::class,
                DemoMode::class,
                UtilityStorage::class,
                Clock::class,
                AppDiagnostics::class,
                AppDispatchers::class,
                SavedStateHandle::class,
            ),
        )
    }
}
