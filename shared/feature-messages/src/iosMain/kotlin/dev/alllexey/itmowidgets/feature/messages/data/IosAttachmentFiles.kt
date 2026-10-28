package dev.alllexey.itmowidgets.feature.messages.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import kotlinx.coroutines.withContext
import okio.FileSystem
import platform.Foundation.NSURL

/**
 * The opened attachment in the app's temporary directory (`tmp/isu-files`), which the screen shows with Quick Look by
 * its `file://` address. Only the latest file is kept.
 */
class IosAttachmentFiles(private val dispatchers: AppDispatchers) : AttachmentFiles {

    private val fileSystem = FileSystem.SYSTEM
    private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / DIRECTORY

    override suspend fun save(name: String, mimeType: String, bytes: ByteArray): String = withContext(dispatchers.io) {
        fileSystem.deleteRecursively(directory)
        fileSystem.createDirectories(directory)
        val path = directory / name
        fileSystem.write(path) { write(bytes) }
        checkNotNull(NSURL.fileURLWithPath(path.toString()).absoluteString)
    }

    override suspend fun clear() = withContext(dispatchers.io) { fileSystem.deleteRecursively(directory) }

    private companion object {
        const val DIRECTORY = "isu-files"
    }
}
