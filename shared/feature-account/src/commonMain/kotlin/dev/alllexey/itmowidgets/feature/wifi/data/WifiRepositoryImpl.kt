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
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiRepository
import kotlinx.coroutines.withContext

/**
 * The Wi-Fi account from ISU's «Настройки Wi-Fi» page through the on-device [IsuWebClient]: one sign-in with the
 * phone's ITMO.ID cookies and one page per [load], kept nowhere. Earlier builds kept the credentials in [SecureStore]
 * under `wifi_credentials.enc` (a Keystore-sealed file in `noBackupFilesDir`, a Keychain item on iOS); every [load]
 * and sign-out delete that item. The demo session answers [DemoWifi] and never reaches ISU.
 */
class WifiRepositoryImpl(
    private val isu: IsuWebClient,
    private val store: SecureStore,
    private val users: CurrentUserProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : WifiRepository, SessionDataCleaner {

    override suspend fun load(): AppResult<WifiAccess> {
        if (demo.isActive()) return AppResult.Success(WifiAccess.Ready(DemoWifi.CREDENTIALS))
        return appResultOf(::errorOf) {
            withContext(dispatchers.io) {
                store.delete(RETIRED_NAME)
                val owner = users.getCurrentUser()?.isu
                val html = isu.signIn()?.page(IsuWifiPage.APP, IsuWifiPage.PAGE)
                html?.let { IsuWifiPage.parse(it, owner) } ?: WifiAccess.SignInRequired
            }
        }
    }

    override suspend fun clearSessionData() {
        withContext(dispatchers.io) { store.delete(RETIRED_NAME) }
    }

    private fun errorOf(failure: Exception): AppError =
        if (failure.isCausedByNetworkFailure()) AppError.Network else AppError.Unknown()

    private companion object {
        /** The credentials earlier builds stored; deleted wherever they are still on a device, never renamed. */
        const val RETIRED_NAME = "wifi_credentials.enc"
    }
}
