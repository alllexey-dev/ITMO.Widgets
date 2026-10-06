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

/** Every Keychain item of the app: the session, the BARS header and whatever a later card stores. */
class KeychainSessionDataCleaner(
    private val store: KeychainSecureStore,
    private val dispatchers: AppDispatchers
) : SessionDataCleaner {

    override suspend fun clearSessionData() = withContext(dispatchers.io) { store.deleteAll() }
}

/**
 * Every file in the App Group container (snapshots, `session-v1.json`), so the extensions show nothing of the old
 * account. The `locks` directory stays: another process may hold a lock in it.
 */
class AppGroupSessionDataCleaner(
    private val directory: AppGroupDirectory,
    private val dispatchers: AppDispatchers,
    private val fileSystem: FileSystem = SystemFileSystem
) : SessionDataCleaner {

    override suspend fun clearSessionData() = withContext(dispatchers.io) {
        fileSystem.listOrNull(directory.root).orEmpty()
            .filterNot { it == directory.locks }
            .forEach(fileSystem::deleteRecursively)
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
