package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.feature.messages.data.AndroidAttachmentFiles
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Android side of ISU messages: attachments in the cache, shared through the `FileProvider`. */
val androidMessagesModule = module {
    single<AttachmentFiles> { AndroidAttachmentFiles(androidContext(), get()) }
}
