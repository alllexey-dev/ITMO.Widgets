package dev.alllexey.itmowidgets.feature.messages.domain

import dev.alllexey.itmowidgets.core.result.AppResult

/**
 * The user's own ISU messages, read on the device with the ITMO.ID session the app holds. Read-only: nothing here
 * sends, deletes or marks a message. Nothing read leaves the device.
 */
interface IsuMessagesRepository {

    /** Page [number] (from 1) of [folder]. `AppError.Unauthorized` when ITMO.ID no longer signs the user in to ISU. */
    suspend fun page(folder: IsuFolder, number: Int): AppResult<IsuMessagesPage>

    /** Downloads [attachment] and saves it for the system viewer, replacing the previous one. */
    suspend fun download(attachment: IsuAttachment): AppResult<AttachmentFile>
}

/** Saves a downloaded attachment where the platform's viewer can open it; the previous file is removed. */
interface AttachmentFiles {
    /** [name] is already one safe path segment; answers the `content://` or `file://` address. */
    suspend fun save(name: String, mimeType: String, bytes: ByteArray): String

    /** Removes the saved files (sign-out). */
    suspend fun clear()
}
