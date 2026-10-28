package dev.alllexey.itmowidgets.feature.messages.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.messages.data.IsuMessagesRepositoryImpl
import dev.alllexey.itmowidgets.feature.messages.domain.IsuMessagesRepository
import dev.alllexey.itmowidgets.feature.messages.presentation.IsuMessagesViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * ISU messages. The platform provides the core types (`IsuWebClient`, `CurrentUserProvider`, `DemoMode`,
 * `AppDispatchers`, `AcademicTimeProvider`) and the feature's `AttachmentFiles` port.
 */
val messagesModule = module {
    // One instance holds the ISU session; sign-out drops it with the saved attachment.
    singleOf(::IsuMessagesRepositoryImpl) { bind<IsuMessagesRepository>() }
    single<SessionDataCleaner>(named("isu-messages")) { get<IsuMessagesRepositoryImpl>() }
    viewModelOf(::IsuMessagesViewModel)
}
