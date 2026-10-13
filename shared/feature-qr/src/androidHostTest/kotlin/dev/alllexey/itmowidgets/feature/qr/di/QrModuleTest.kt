package dev.alllexey.itmowidgets.feature.qr.di

import androidx.datastore.core.DataStore
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class QrModuleTest {

    /**
     * The data and the screen resolve inside the module; only the core types of the platform's Koin modules are
     * given.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theQrModuleResolvesWithThePlatformCoreTypes() {
        qrModule.verify(
            extraTypes = listOf(
                MyItmoClient::class,
                DataStore::class,
                AppDirectories::class,
                QrSettingsPreferences::class,
                DeviceHintPreferences::class,
                DemoMode::class,
                AppDispatchers::class,
                Clock::class,
                SavedStateHandle::class,
                SessionRepository::class,
                SocialRepository::class,
            ),
        )
    }
}
