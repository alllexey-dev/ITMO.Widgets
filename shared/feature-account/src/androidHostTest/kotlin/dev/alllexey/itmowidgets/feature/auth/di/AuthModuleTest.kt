package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.FcmTokenSync
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.BackendIdentitySync
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionLifecycleEffects
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.feature.auth.data.SessionDataCleaners
import kotlin.test.Test
import kotlin.time.Clock
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class AuthModuleTest {

    /** The session and the demo switch resolve over the ports the platform supplies (`CoreBridge` on Android). */
    @Test
    fun theAuthDataModuleResolvesWithThePlatformPorts() {
        authDataModule.verify(extraTypes = platformPorts)
    }

    /** The sign-in screens resolve over the session [authDataModule] builds. */
    @Test
    fun theAuthModulesResolveWithThePlatformPorts() {
        module { includes(authDataModule, authModule) }.verify(extraTypes = platformPorts)
    }

    private val platformPorts = listOf(
        SessionTokenStore::class,
        MyItmoClient::class,
        Clock::class,
        CurrentUserProvider::class,
        SessionDataCleaners::class,
        SessionLifecycleEffects::class,
        BackendIdentitySync::class,
        BackendDeviceSession::class,
        FcmTokenSync::class,
        AppDiagnostics::class,
        DemoPreferences::class,
        AppDispatchers::class,
    )
}
