package dev.alllexey.itmowidgets.feature.connections.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.connections.ConnectionsApi
import dev.alllexey.itmowidgets.core.connections.SignInLabel
import dev.alllexey.itmowidgets.core.connections.ThisPhone
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.ItmoIdCookies
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.SignInLabelPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.connections.domain.HpkeSender
import io.ktor.client.engine.HttpClientEngine
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

class ConnectionsModuleTest {

    /**
     * Core 2.0's connections area, the gate, the demo switch, the dispatchers, the time, the session and its cookies,
     * the label switch, the notifier and this phone are core or platform bindings; MyItmoApi's client, the engine,
     * the clock and the HPKE sender come from the platform.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theConnectionsModulesResolveWithThePlatformTypes() {
        module { includes(connectionsDataModule, connectionsModule) }.verify(
            extraTypes = listOf(
                ConnectionsApi::class,
                CurrentUserProvider::class,
                BackendGate::class,
                DemoMode::class,
                AppDispatchers::class,
                AcademicTimeProvider::class,
                SavedStateHandle::class,
                MyItmoClient::class,
                HttpClientEngine::class,
                Clock::class,
                HpkeSender::class,
                ItmoIdCookies::class,
                SessionTokenStore::class,
                SignInLabel::class,
                SignInLabelPreferences::class,
                AppNotifier::class,
                ThisPhone::class,
                String::class,
            ),
        )
    }
}
