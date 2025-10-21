package dev.alllexey.itmowidgets.data.local

interface QrCodeLocalDataSource {

    fun getQrHex(): String?

    fun saveQrHex(hex: String)

    fun clearCache()
}