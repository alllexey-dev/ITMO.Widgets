package dev.alllexey.itmowidgets.feature.me.di

import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class MeModuleTest {

    /** The session, social data and the opt-in are bridged from the app's Hilt graph. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theMeModuleResolvesWithTheBridgedTypes() {
        meModule.verify(
            extraTypes = listOf(SessionRepository::class, SocialRepository::class, CustomServicesRepository::class),
        )
    }
}
