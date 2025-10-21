package dev.alllexey.itmowidgets.data.repository

import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSource

class QrCodeRepository(
    private val localDataSource: QrCodeLocalDataSource,
    private val remoteDataSource: QrCodeRemoteDataSource
) {

    suspend fun getQrHex(): String {
        val cachedHex = localDataSource.getQrHex()
        if (cachedHex != null) {
            return cachedHex
        }

        val remoteHex = remoteDataSource.getQrHex()
            ?: throw RuntimeException("API returned null QR code")

        localDataSource.saveQrHex(remoteHex)
        return remoteHex
    }

    fun clearCache() {
        localDataSource.clearCache()
    }
}