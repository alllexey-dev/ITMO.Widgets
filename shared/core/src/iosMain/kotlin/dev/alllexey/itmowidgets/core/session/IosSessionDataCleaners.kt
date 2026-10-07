package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.platform.WebsiteDataClearer
import dev.alllexey.itmowidgets.core.storage.AppGroupDirectory
import dev.alllexey.itmowidgets.core.storage.KeychainSecureStore
import dev.alllexey.itmowidgets.core.storage.SystemFileSystem
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/** Every Keychain item of the app: the session, the BARS header and whatever a later card stores. */
class KeychainSessionDataCleaner(
    private val store: KeychainSecureStore,
    private val dispatchers: AppDispatchers
) : SessionDataCleaner {

    override suspend fun clearSessionData() = withContext(dispatchers.io) { store.deleteAll() }
}

/**
 * Every file the app keeps in the App Group container (snapshots, `session-v1.json`, a writer's leftover temporary
 * file), so the extensions show nothing of the old account. Stays:
 * - `locks`: another process may hold a lock in it;
 * - the container's own entries, every other hidden file and `Library`: without
 *   `.com.apple.mobile_container_manager.metadata.plist` the system drops the container as stale and gives the next
 *   process a new one, so the app and the widgets would each read a container of their own.
 */
class AppGroupSessionDataCleaner(
    private val directory: AppGroupDirectory,
    private val dispatchers: AppDispatchers,
    private val fileSystem: FileSystem = SystemFileSystem
) : SessionDataCleaner {

    override suspend fun clearSessionData() = withContext(dispatchers.io) {
        fileSystem.listOrNull(directory.root).orEmpty()
            .filter(::isAppEntry)
            .forEach(fileSystem::deleteRecursively)
    }

    private fun isAppEntry(entry: Path): Boolean = when {
        entry == directory.locks -> false
        entry.name.startsWith(HIDDEN) -> entry.name.endsWith(TEMPORARY)
        else -> entry.name != SYSTEM_LIBRARY
    }

    private companion object {
        const val HIDDEN = "."
        const val TEMPORARY = ".tmp"
        const val SYSTEM_LIBRARY = "Library"
    }
}

/** WebKit's cookies and storage, so a web sign-in never outlives a native sign-out or an account switch. */
class WebsiteDataSessionDataCleaner(
    private val clearer: WebsiteDataClearer,
    private val dispatchers: AppDispatchers
) : SessionDataCleaner {

    override suspend fun clearSessionData() = withContext(dispatchers.main) {
        suspendCancellableCoroutine { continuation ->
            clearer.clearWebsiteData { if (continuation.isActive) continuation.resume(Unit) }
        }
    }
}
