package dev.alllexey.itmowidgets.feature.qr.data.remote

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.awaitResponse
import javax.inject.Inject

class QrCodeRemoteDataSourceImpl @Inject constructor(
    private val myItmo: MyItmo,
    private val demo: DemoMode
) : QrCodeRemoteDataSource {

    override suspend fun getQrHex(): String {
        if (demo.isActive()) return DemoQr.HEX
        return withContext(Dispatchers.IO) {
            var response = myItmo.api.getQrCode().awaitResponse()
            if (response.body()?.response?.qrHex == null) {
                myItmo.forceRefreshTokens()
                response = myItmo.api.getQrCode().awaitResponse()
            }

            if (!response.isSuccessful) {
                throw HttpException(response)
            }

            response.body()?.response?.qrHex
                ?: error("MyITMO returned an empty QR code")
        }
    }
}
