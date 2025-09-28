package me.alllexey123.itmowidgets.data.remote

interface QrCodeRemoteDataSource {

    suspend fun getQrHex(): String?
}