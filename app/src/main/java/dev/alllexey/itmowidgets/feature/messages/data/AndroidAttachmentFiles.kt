package dev.alllexey.itmowidgets.feature.messages.data

import android.content.Context
import androidx.core.content.FileProvider
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.feature.messages.domain.AttachmentFiles
import java.io.File
import kotlinx.coroutines.withContext

/**
 * The opened ISU attachment in `cacheDir/isu-files`, handed to the system viewer through the app's `FileProvider`
 * (`content://`, read permission for that one intent). Only the latest file is kept.
 */
class AndroidAttachmentFiles(private val context: Context, private val dispatchers: AppDispatchers) : AttachmentFiles {

    private val directory get() = File(context.cacheDir, DIRECTORY)

    override suspend fun save(name: String, mimeType: String, bytes: ByteArray): String = withContext(dispatchers.io) {
        val directory = directory
        directory.deleteRecursively()
        check(directory.mkdirs())
        val file = File(directory, name).apply { writeBytes(bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString()
    }

    override suspend fun clear() {
        withContext(dispatchers.io) { directory.deleteRecursively() }
    }

    companion object {
        /** Matches `res/xml/file_paths.xml`. */
        const val DIRECTORY = "isu-files"

        /** Matches the provider in the manifest: `${applicationId}.files`. */
        const val AUTHORITY_SUFFIX = ".files"
    }
}
