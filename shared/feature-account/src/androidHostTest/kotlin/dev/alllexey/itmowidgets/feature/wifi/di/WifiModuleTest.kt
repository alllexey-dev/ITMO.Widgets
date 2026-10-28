package dev.alllexey.itmowidgets.feature.wifi.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.SecureStore
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class WifiModuleTest {

    /** The ISU client, the secure store, the user, the demo switch and the dispatchers come from the platform. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theWifiModuleResolvesWithThePlatformTypes() {
        wifiModule.verify(
            extraTypes = listOf(
                IsuWebClient::class,
                SecureStore::class,
                CurrentUserProvider::class,
                DemoMode::class,
                AppDispatchers::class,
            ),
        )
    }
}
