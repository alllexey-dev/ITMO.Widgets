package dev.alllexey.itmowidgets.data.remote

interface QrCodeRemoteDataSource {

    suspend fun getQrHex(): String?
}