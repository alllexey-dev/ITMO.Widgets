package dev.alllexey.itmowidgets.core.storage

import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.platform.BundleIdentifiers
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager

/**
 * The App Group container that the app, the widget extension and the notification service share: versioned
 * snapshots (`<name>-v<N>.json`), `session-v1.json` and the [FileCrossProcessLock] files under `locks/`. The files
 * are listed in docs/ios.md, Data sharing.
 *
 * [isShared] is false when no group container resolves (an unsigned build, SP-23): the directory then sits in the
 * app's own container, so the app keeps working and only the extensions see nothing.
 */
class AppGroupDirectory internal constructor(
    val root: Path,
    val isShared: Boolean
) {

    /** Where [FileCrossProcessLock] keeps one file per lock name. */
    val locks: Path
        get() = root / LOCKS

    /** A file directly in the group container; [name] is a plain file name. */
    fun file(name: String): Path {
        require(name.isNotEmpty() && '/' !in name && name != "." && name != "..") { "'$name' is not a file name" }
        return root / name
    }

    @OptIn(ExperimentalAtomicApi::class)
    companion object {

        /**
         * The group container of the first ID in [BundleIdentifiers.appGroupCandidates] that resolves, created;
         * without one, `<noBackup>/app-group` in the app container, logged once per process and never a crash.
         */
        fun resolve(
            identifiers: BundleIdentifiers,
            appDirectories: AppDirectories,
            log: AppLog,
            fileSystem: FileSystem = SystemFileSystem,
            containerOf: (groupId: String) -> Path? = ::systemContainer
        ): AppGroupDirectory {
            val container = identifiers.appGroupCandidates.firstNotNullOfOrNull(containerOf)
            val directory = if (container != null) {
                AppGroupDirectory(container, isShared = true)
            } else {
                if (fallbackLogged.compareAndSet(expectedValue = false, newValue = true)) {
                    log.warn(TAG, "No App Group container; extensions will not see shared files")
                }
                AppGroupDirectory(appDirectories.noBackup / FALLBACK, isShared = false)
            }
            fileSystem.createDirectories(directory.root)
            return directory
        }

        private fun systemContainer(groupId: String): Path? = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(groupId)
            ?.path
            ?.toPath()

        private val fallbackLogged = AtomicBoolean(false)

        private const val LOCKS = "locks"
        private const val FALLBACK = "app-group"
        private const val TAG = "AppGroupDirectory"
    }
}
