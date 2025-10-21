package dev.alllexey.itmowidgets.data.local

import android.content.Context
import java.io.File
import java.nio.file.Files

class QrCodeLocalDataSourceImpl(
    private val context: Context
) : QrCodeLocalDataSource {

    private fun cacheFile(): File {
        return File(context.cacheDir, "qr_hex").apply { parentFile?.mkdirs() }
    }

    override fun getQrHex(): String? {
        return try {
            Files.readAllLines(cacheFile().toPath()).firstOrNull()?.takeIf { it.length >= 6 }
        } catch (_: Exception) {
            null
        }
    }

    override fun saveQrHex(hex: String) {
        try {
            Files.write(cacheFile().toPath(), listOf(hex))
        } catch (_: Exception) {
        }
    }

    override fun clearCache() {
        cacheFile().delete()
    }
}