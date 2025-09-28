package me.alllexey123.itmowidgets.data.remote

import api.myitmo.MyItmo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse

class QrCodeRemoteDataSourceImpl(
    private val myItmo: MyItmo
) : QrCodeRemoteDataSource {

    override suspend fun getQrHex(): String? {
        return withContext(Dispatchers.IO) {
            var response = myItmo.api().getQrCode().awaitResponse()
            // try to refresh tokens if null (possible fix)
            if (response.body()?.response?.qrHex == null) {
                myItmo.refreshTokens(myItmo.storage.refreshToken)
                response = myItmo.api().getQrCode().awaitResponse()
            }

            response.body()?.response?.qrHex
        }
    }
}