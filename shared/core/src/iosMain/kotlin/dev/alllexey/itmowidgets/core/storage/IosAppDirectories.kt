package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.numberWithBool

/**
 * The app's private directories in its own container. Only the app process uses them, DataStore's
 * `app_preferences` included (DataStore is single-process); what an extension reads lives in [AppGroupDirectory].
 *
 * - [files]: `Library/Application Support/files`, backed up.
 * - [cache]: `Library/Caches`, which the system may clear.
 * - [noBackup]: `Library/Application Support/no-backup`, excluded from iCloud and device backups.
 */
class IosAppDirectories internal constructor(
    override val files: Path,
    override val cache: Path,
    override val noBackup: Path
) : AppDirectories {

    companion object {

        /** The app container's directories, created, with [noBackup] marked as excluded from backup. */
        fun create(log: AppLog, fileSystem: FileSystem = SystemFileSystem): IosAppDirectories = create(
            applicationSupport = systemDirectory(NSApplicationSupportDirectory),
            caches = systemDirectory(NSCachesDirectory),
            log = log,
            fileSystem = fileSystem
        )

        internal fun create(
            applicationSupport: Path,
            caches: Path,
            log: AppLog,
            fileSystem: FileSystem = SystemFileSystem
        ): IosAppDirectories {
            val directories = IosAppDirectories(
                files = applicationSupport / FILES,
                cache = caches,
                noBackup = applicationSupport / NO_BACKUP
            )
            with(directories) { listOf(files, cache, noBackup) }.forEach(fileSystem::createDirectories)
            // Set on every start: the flag is lost when the directory is ever re-created.
            if (!excludeFromBackup(directories.noBackup)) {
                log.warn(TAG, "Could not exclude no-backup from backup")
            }
            return directories
        }

        @OptIn(ExperimentalForeignApi::class)
        private fun systemDirectory(directory: NSSearchPathDirectory): Path {
            val url = NSFileManager.defaultManager.URLForDirectory(
                directory = directory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null
            )
            return checkNotNull(url?.path) { "No system directory $directory in the app container" }.toPath()
        }

        @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
        private fun excludeFromBackup(directory: Path): Boolean = NSURL.fileURLWithPath(directory.toString(), true)
            .setResourceValue(NSNumber.numberWithBool(true), NSURLIsExcludedFromBackupKey, null)

        private const val FILES = "files"
        private const val NO_BACKUP = "no-backup"
        private const val TAG = "IosAppDirectories"
    }
}
