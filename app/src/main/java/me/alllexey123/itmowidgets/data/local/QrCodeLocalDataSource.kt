package me.alllexey123.itmowidgets.data.local

interface QrCodeLocalDataSource {

    fun getQrHex(): String?

    fun saveQrHex(hex: String)

    fun clearCache()
}