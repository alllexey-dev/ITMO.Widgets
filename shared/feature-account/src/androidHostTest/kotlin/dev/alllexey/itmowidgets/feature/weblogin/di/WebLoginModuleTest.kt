package dev.alllexey.itmowidgets.feature.weblogin.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.pairing.PairingApi
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.weblogin.domain.HpkeSender
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class WebLoginModuleTest {

    /**
     * Core 2.0's users and pairing areas, the signed-in user, the gate, the demo switch, the dispatchers and the academic time are core bindings; the
     * MyItmoApi client, its token storage and the clock come from the platform, as does the HPKE sender.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theWebLoginModulesResolveWithThePlatformTypes() {
        module { includes(webLoginDataModule, webLoginModule) }.verify(
            extraTypes = listOf(
                UsersApi::class,
                PairingApi::class,
                CurrentUserProvider::class,
                BackendGate::class,
                DemoMode::class,
                AppDispatchers::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
                MyItmoClient::class,
                TokenStorage::class,
                Clock::class,
                HpkeSender::class,
            ),
        )
    }
}
