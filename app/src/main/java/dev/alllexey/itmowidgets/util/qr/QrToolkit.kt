package dev.alllexey.itmowidgets.util.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ContextThemeWrapper
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.AppContainer
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.data.local.QrBitmapCache
import dev.alllexey.itmowidgets.data.local.QrBitmapCacheImpl
import dev.alllexey.itmowidgets.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.data.repository.QrCodeRepository

class QrToolkit(
    private val appContainer: AppContainer,
    private val context: Context = appContainer.context,
    val repository: QrCodeRepository = QrCodeRepository(
        QrCodeLocalDataSourceImpl(context),
        QrCodeRemoteDataSourceImpl(appContainer.myItmo)
    ),
    val generator: QrCodeGenerator = QrCodeGenerator(),
    val renderer: QrBitmapRenderer = QrBitmapRenderer(),
    val bitmapCache: QrBitmapCache = QrBitmapCacheImpl(context)
) {

    suspend fun getQrHex(allowCached: Boolean = true): String {
        return repository.getQrHex(allowCached)
    }

    fun generateQrBitmap(qrHex: String): Bitmap {
        val (bgColor, fgColor) = getQrColors()

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
        return generateNoiseBitmap(noCache)
    }

    fun generateNoiseBitmap(noCache: Boolean = false): Bitmap {
        val (bgColor, fgColor) = getQrColors()
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
        val (bgColor, _) = getQrColors()
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

    fun getQrColors(): Pair<Int, Int> {
        val dynamicColorsState = appContainer.storage.settings.getDynamicQrColorsState()
        return getQrColors(dynamicColorsState)
    }

    // [background, foreground]
    fun getQrColors(dynamic: Boolean): Pair<Int, Int> {
        var darkModule: Int
        var lightBg: Int

        if (dynamic) {
            val themedContext = ContextThemeWrapper(context, R.style.AppTheme)
            lightBg = MaterialColors.getColor(
                themedContext,
                com.google.android.material.R.attr.colorSurface,
                Color.WHITE
            )
            darkModule = MaterialColors.getColor(
                themedContext,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.BLACK
            )

            // swap
            if (lightBg.isDark()) {
                lightBg = darkModule.also { darkModule = lightBg }
            }

            val darkModuleVariant = MaterialColors.getColor(
                themedContext,
                com.google.android.material.R.attr.colorOnSurface,
                Color.BLACK
            )

            darkModule = maxOf(
                darkModule,
                darkModuleVariant,
                Comparator.comparingDouble { value -> value.darkness() })
        } else {
            darkModule = Color.BLACK
            lightBg = Color.WHITE
        }

        return lightBg to darkModule
    }

    fun Int.isDark(): Boolean {
        return darkness() >= 0.5
    }

    fun Int.darkness(): Double {
        return 1 - (0.299 * Color.red(this) + 0.587 * Color.green(this) + 0.114 * Color.blue(this)) / 255
    }
}