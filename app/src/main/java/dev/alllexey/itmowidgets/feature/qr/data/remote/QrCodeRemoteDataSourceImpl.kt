package dev.alllexey.itmowidgets.feature.qr.data.remote

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The pass from qr.itmo.su through MyItmoApi 2.x. The client's auth plugin already answers a 401 with one refresh
 * and one retry, so the retry here covers only a successful answer without a pass: one forced refresh, one more
 * request, then [MyItmoException.Decode]. Every other failure is thrown at once and mapped by the repository.
 */
class QrCodeRemoteDataSourceImpl @Inject constructor(
    private val client: MyItmoClient,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : QrCodeRemoteDataSource {

    override suspend fun getQrHex(): String {
        if (demo.isActive()) return DemoQr.HEX
        return withContext(dispatchers.io) {
            fetchQrHex() ?: run {
                client.tokens.forceRefresh()
                fetchQrHex()
            } ?: throw MyItmoException.Decode()
        }
    }

    /** The pass, or null when the answer has none; the 2.x model turns an absent `qr_hex` into an empty string. */
    private suspend fun fetchQrHex(): String? =
        client.qr.getQrCode().response?.qrHex?.takeIf { it.isNotBlank() }
}
