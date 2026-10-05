package dev.alllexey.itmowidgets.core.storage

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath

/** [AppDirectories] of the application [context]. */
class AndroidAppDirectories(context: Context) : AppDirectories {

    private val context = context.applicationContext

    override val files: Path get() = context.filesDir.toOkioPath()

    override val cache: Path get() = context.cacheDir.toOkioPath()

    override val noBackup: Path get() = context.noBackupFilesDir.toOkioPath()
}
