package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.core.session.SessionRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class AuthModuleTest {

    /** The session is bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theAuthModuleResolvesWithTheBridgedSession() {
        authModule.verify(extraTypes = listOf(SessionRepository::class))
    }
}
