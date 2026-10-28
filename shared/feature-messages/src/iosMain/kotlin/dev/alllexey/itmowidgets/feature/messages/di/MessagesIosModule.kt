package dev.alllexey.itmowidgets.feature.messages.di

import dev.alllexey.itmowidgets.feature.messages.data.IosAttachmentFiles
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** The iOS bindings of [messagesModule]: attachments in the temporary directory. */
val messagesIosModule: Module = module {
    singleOf(::IosAttachmentFiles) { bind<AttachmentFiles>() }
}
