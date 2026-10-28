package dev.alllexey.itmowidgets.feature.messages.di

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class MessagesModuleTest {

    /** Everything resolves inside the module but the platform's core types and the attachment port. */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun theMessagesModuleResolvesWithThePlatformTypes() {
        messagesModule.verify(
            extraTypes = listOf(
                IsuWebClient::class,
                CurrentUserProvider::class,
                AttachmentFiles::class,
                DemoMode::class,
                AppDispatchers::class,
                AcademicTimeProvider::class,
            )
        )
    }
}
