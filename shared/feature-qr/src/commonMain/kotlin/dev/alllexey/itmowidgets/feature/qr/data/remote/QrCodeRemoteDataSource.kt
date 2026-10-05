package dev.alllexey.itmowidgets.feature.qr.data.remote

interface QrCodeRemoteDataSource {

    suspend fun getQrHex(): String
}
