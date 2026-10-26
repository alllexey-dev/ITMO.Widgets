package dev.alllexey.itmowidgets.feature.wifi.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.IsuWebClient
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The Wi-Fi account from ISU's «Настройки Wi-Fi» page through the on-device [IsuWebClient]: one sign-in with the
 * phone's ITMO.ID cookies and one page per [refresh]. The credentials stay on the phone, in [SecureStore] under
 * `wifi_credentials.enc` (on Android a Keystore-sealed file in `noBackupFilesDir`, on iOS a Keychain item), bound to
 * the owner's ISU; sign-out deletes them. The demo session answers [DemoWifi] and never reaches ISU.
 */
class WifiRepositoryImpl(
    private val isu: IsuWebClient,
    private val store: SecureStore,
    private val users: CurrentUserProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : WifiRepository, SessionDataCleaner {
    private val lock = Mutex()

    override suspend fun cached(): WifiCredentials? {
        if (demo.isActive()) return DemoWifi.CREDENTIALS
        val owner = users.getCurrentUser()?.isu ?: return null
        return withContext(dispatchers.io) { lock.withLock { load(owner) } }
    }

    override suspend fun refresh(): AppResult<WifiAccess> {
        if (demo.isActive()) return AppResult.Success(WifiAccess.Ready(DemoWifi.CREDENTIALS))
        return appResultOf(::errorOf) {
            withContext(dispatchers.io) {
                val owner = users.getCurrentUser()?.isu
                val html = isu.signIn()?.page(IsuWifiPage.APP, IsuWifiPage.PAGE)
                val access = html?.let { IsuWifiPage.parse(it, owner) } ?: WifiAccess.SignInRequired
                if (owner != null) keep(owner, access)
                access
            }
        }
    }

    override suspend fun clearSessionData() {
        withContext(dispatchers.io) { lock.withLock { store.delete(NAME) } }
    }

    /** A read password replaces the stored one, a page without one removes it; no answer keeps it. */
    private suspend fun keep(owner: Int, access: WifiAccess) = lock.withLock {
        when (access) {
            is WifiAccess.Ready -> store.write(NAME, listOf(owner, access.credentials.login, access.credentials.password).joinToString("\n"))
            WifiAccess.NoPassword -> store.delete(NAME)
            WifiAccess.SignInRequired -> Unit
        }
    }

    /** `"<isu>\n<login>\n<password>"`; a value of another ISU or one that cannot be read is none. */
    private fun load(owner: Int): WifiCredentials? = runCatching {
        val parts = store.read(NAME)?.split('\n', limit = 3) ?: return null
        if (parts.size != 3 || parts[0] != owner.toString()) return null
        WifiCredentials(parts[1], parts[2])
    }.getOrNull()

    private fun errorOf(failure: Exception): AppError =
        if (failure.isCausedByNetworkFailure()) AppError.Network else AppError.Unknown()

    private companion object {
        /** The stable name of the stored credentials; never renamed. */
        const val NAME = "wifi_credentials.enc"
    }
}
