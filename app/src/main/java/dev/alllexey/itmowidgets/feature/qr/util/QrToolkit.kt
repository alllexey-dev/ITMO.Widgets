package dev.alllexey.itmowidgets.feature.qr.util

import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.alllexey.itmowidgets.domain.repository.QrBitmapCache
import dev.alllexey.itmowidgets.domain.repository.QrBitmapCache.Companion.bitmapMeta
import dev.alllexey.itmowidgets.domain.repository.QrCodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class QrToolkit @Inject constructor(
    val repository: QrCodeRepository,
    val bitmapCache: QrBitmapCache,
    val generator: QrCodeGenerator,
    val colorResolver: QrColorResolver,
    val renderer: QrBitmapRenderer,
    val customSpoilerManager: CustomSpoilerManager
) {

    fun observeQrHex(): Flow<String> = repository.observeQrHex()

    suspend fun refreshQrHex(allowCached: Boolean) = repository.refreshQrHex(!allowCached)

    fun generateQrBitmap(qrHex: String): Bitmap {
        val (bgColor, fgColor) = colorResolver.getQrColors()

        val qrCode = generator.generate(qrHex)
        val qrCodeBooleans = generator.toBooleans(qrCode)

        return renderer.render(
            qrCode = qrCodeBooleans,
            size = defaultSize(),
            backgroundColor = bgColor,
            foregroundColor = fgColor,
            relativePadding = defaultRelativePadding(),
            relativeRounding = defaultRounding()
        )
    }

    fun generateSpoilerBitmap(noCache: Boolean = false): Bitmap {
        val customSpoiler = customSpoilerManager.getCustomSpoilerBitmap()
        if (customSpoiler != null) {
            if (customSpoiler.width != defaultSize() || customSpoiler.height != defaultSize()) {
                return customSpoiler.scale(defaultSize(), defaultSize())
            }
            return customSpoiler
        }
        return generateNoiseBitmap(noCache)
    }

    fun generateNoiseBitmap(noCache: Boolean = false): Bitmap {
        val (bgColor, fgColor) = colorResolver.getQrColors()
        val size = defaultSize()

        val meta = bitmapMeta("n", size, bgColor, fgColor)
        if (!noCache) {
            val cachedBitmap =
                bitmapCache.getBitmap("noise", meta)
            if (cachedBitmap != null) return cachedBitmap
        }

        val bitmap = renderer.renderNoise(
            size = size,
            backgroundColor = bgColor,
            foregroundColor = fgColor,
            relativePadding = defaultRelativePadding(),
            relativeRounding = defaultRounding()
        )

        bitmapCache.saveBitmap("noise", meta, bitmap)
        return bitmap
    }

    fun resetNoise() {
        bitmapCache.clearCache("noise")
    }

    fun generateEmptyQrBitmap(): Bitmap {
        val (bgColor, _) = colorResolver.getQrColors()
        return renderer.renderEmpty(
            size = defaultSize(),
            color = bgColor,
            relativeRounding = defaultRounding()
        )
    }

    fun defaultRounding(): Float {
        return 0.06F
    }

    fun defaultSize(): Int {
        return 420
    }

    fun defaultRelativePadding(): Float {
        return 0.05F
    }
}
