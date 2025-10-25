package dev.alllexey.itmowidgets.util.qr

import android.content.Context
import android.graphics.Bitmap
import dev.alllexey.itmowidgets.AppContainer
import dev.alllexey.itmowidgets.data.local.QrBitmapCache
import dev.alllexey.itmowidgets.data.local.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.repository.QrCodeRepository
import androidx.core.graphics.scale

class QrToolkit(
    private val appContainer: AppContainer,
    private val context: Context = appContainer.context,
    val repository: QrCodeRepository = QrCodeRepository(
        QrCodeLocalDataSourceImpl(context),
        QrCodeRemoteDataSourceImpl(appContainer.myItmo)
    ),
    val generator: QrCodeGenerator = QrCodeGenerator(),
    val renderer: QrBitmapRenderer = QrBitmapRenderer(),
    val colorResolver: QrColorResolver = QrColorResolver(appContainer),
    val bitmapCache: QrBitmapCache = QrBitmapCacheImpl(context),
    val customSpoilerManager: CustomSpoilerManager = CustomSpoilerManager(context)
) {

    suspend fun getQrHex(allowCached: Boolean = true): String {
        return repository.getQrHex(allowCached)
    }

    fun generateQrBitmap(qrHex: String): Bitmap {
        val (bgColor, fgColor) = colorResolver.getQrColors()

        val qrCode = generator.generate(qrHex)
        val qrCodeBooleans = generator.toBooleans(qrCode)

        return renderer.render(
            qrCode = qrCodeBooleans,
            qrSidePixels = defaultSidePixels(),
            backgroundColor = bgColor,
            foregroundColor = fgColor,
            relativePadding = defaultRelativePadding(),
            relativeRounding = defaultRounding()
        )
    }

    fun generateSpoilerBitmap(noCache: Boolean = false): Bitmap {
        val customSpoiler = customSpoilerManager.getCustomSpoilerBitmap()
        if (customSpoiler != null) {
            if (customSpoiler.width != defaultSidePixels() || customSpoiler.height != defaultSidePixels()) {
                return customSpoiler.scale(defaultSidePixels(), defaultSidePixels())
            }
            return customSpoiler
        }
        return generateNoiseBitmap(noCache)
    }

    fun generateNoiseBitmap(noCache: Boolean = false): Bitmap {
        val (bgColor, fgColor) = colorResolver.getQrColors()
        val qrSidePixels = defaultSidePixels()

        if (!noCache) {
            val cachedBitmap =
                bitmapCache.loadNoiseBitmap(qrSidePixels, bgColor, fgColor)
            if (cachedBitmap != null) return cachedBitmap
        }

        val result = renderer.renderNoise(
            qrSidePixels = qrSidePixels,
            backgroundColor = bgColor,
            foregroundColor = fgColor,
            relativePadding = defaultRelativePadding(),
            relativeRounding = defaultRounding()
        )

        bitmapCache.saveNoiseBitmap(result, qrSidePixels, bgColor, fgColor)
        return result
    }

    fun generateEmptyQrBitmap(): Bitmap {
        val (bgColor, _) = colorResolver.getQrColors()
        return renderer.renderEmpty(
            qrSidePixels = defaultSidePixels(),
            color = bgColor,
            relativeRounding = defaultRounding()
        )
    }

    fun defaultRounding(): Float {
        return 0.06F
    }

    fun defaultSidePixels(): Int {
        return 420
    }

    fun defaultRelativePadding(): Float {
        return 0.05F
    }
}