package me.alllexey123.itmowidgets.util

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.core.graphics.createBitmap
import com.google.android.material.color.MaterialColors
import me.alllexey123.itmowidgets.R
import kotlin.random.Random
import kotlin.random.nextInt

class QrBitmapRenderer(
    private val context: Context
) {


    fun render(
        qrCode: List<List<Boolean>>,
        qrSidePixels: Int = defaultSidePixels(),
        relativePadding: Float = defaultRelativePadding(),
        bgRoundingPixels: Float = defaultBgRounding(),
        dynamic: Boolean
    ): Bitmap {
        val colors = getQrColors(dynamic)
        return render(
            qrCode,
            qrSidePixels,
            colors.first,
            colors.second,
            relativePadding,
            bgRoundingPixels
        )
    }

    fun renderEmpty(
        qrSidePixels: Int = defaultSidePixels(),
        relativePadding: Float = defaultRelativePadding(),
        bgRoundingPixels: Float = defaultBgRounding(),
        dynamic: Boolean
    ): Bitmap {
        val colors = getQrColors(dynamic)
        return renderFilled(
            qrSidePixels,
            colors.first,
            colors.first,
            relativePadding,
            bgRoundingPixels
        )
    }

    fun renderFull(
        qrSidePixels: Int = defaultSidePixels(),
        relativePadding: Float = defaultRelativePadding(),
        bgRoundingPixels: Float = defaultBgRounding(),
        dynamic: Boolean
    ): Bitmap {
        val colors = getQrColors(dynamic)
        return renderFilled(
            qrSidePixels,
            colors.first,
            colors.second,
            relativePadding,
            bgRoundingPixels
        )
    }

    fun renderFilled(
        qrSidePixels: Int = defaultSidePixels(),
        backgroundColor: Int,
        foregroundColor: Int,
        relativePadding: Float = defaultRelativePadding(),
        bgRoundingPixels: Float = defaultBgRounding()
    ): Bitmap {
        val booleans = List(21) { MutableList(21) { true } }
        return render(
            booleans,
            qrSidePixels,
            backgroundColor,
            foregroundColor,
            relativePadding,
            bgRoundingPixels
        )
    }


    // padding: [0, 0.5]
    fun render(
        qrCode: List<List<Boolean>>,
        qrSidePixels: Int,
        backgroundColor: Int,
        foregroundColor: Int,
        relativePadding: Float,
        bgRoundingPixels: Float
    ): Bitmap {
        val qrSize = qrCode.size
        val bitmap = createBitmap(qrSidePixels, qrSidePixels)
        val canvas = Canvas(bitmap)

        val foregroundPaint = Paint().apply {
            color = foregroundColor
            isAntiAlias = true
        }

        val backgroundPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
        }

        val moduleSidePixels = qrSidePixels * (1 - relativePadding * 2) / qrSize
        val moduleCornerRadius = moduleSidePixels * 0.5f

        // draw rounded background
        val bg = Path().apply {
            addRoundRect(
                RectF(0F, 0F, qrSidePixels.toFloat(), qrSidePixels.toFloat()),
                bgRoundingPixels,
                bgRoundingPixels,
                Path.Direction.CW
            )
        }

        val paddingPixels = qrSidePixels * relativePadding
        canvas.drawPath(bg, backgroundPaint)

        // draw the black modules
        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                if (qrCode[x][y]) {
                    val left = x * moduleSidePixels + paddingPixels
                    val top = y * moduleSidePixels + paddingPixels
                    val right = left + moduleSidePixels
                    val bottom = top + moduleSidePixels

                    val topN = qrCode.getOrNull(x)?.getOrNull(y - 1) == true
                    val bottomN = qrCode.getOrNull(x)?.getOrNull(y + 1) == true
                    val leftN = qrCode.getOrNull(x - 1)?.getOrNull(y) == true
                    val rightN = qrCode.getOrNull(x + 1)?.getOrNull(y) == true

                    val radii = floatArrayOf(
                        if (!topN && !leftN) moduleCornerRadius else 0f,
                        if (!topN && !leftN) moduleCornerRadius else 0f,

                        if (!topN && !rightN) moduleCornerRadius else 0f,
                        if (!topN && !rightN) moduleCornerRadius else 0f,

                        if (!bottomN && !rightN) moduleCornerRadius else 0f,
                        if (!bottomN && !rightN) moduleCornerRadius else 0f,

                        if (!bottomN && !leftN) moduleCornerRadius else 0f,
                        if (!bottomN && !leftN) moduleCornerRadius else 0f
                    )

                    val path = Path().apply {
                        addRoundRect(RectF(left, top, right, bottom), radii, Path.Direction.CW)
                    }
                    canvas.drawPath(path, foregroundPaint)
                }
            }
        }

        // redraw the white modules (round corners)
        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                if (!qrCode[x][y]) {
                    val x1 = x * moduleSidePixels + paddingPixels
                    val y1 = y * moduleSidePixels + paddingPixels
                    val x2 = x1 + moduleSidePixels
                    val y2 = y1 + moduleSidePixels
                    val rect = RectF(x1, y1, x2, y2)

                    var bottomRight = 0f
                    var bottomLeft = 0f
                    var topLeft = 0f
                    var topRight = 0f

                    val top = qrCode.getOrNull(x)?.getOrNull(y - 1) == true
                    val right = qrCode.getOrNull(x + 1)?.getOrNull(y) == true
                    val bottom = qrCode.getOrNull(x)?.getOrNull(y + 1) == true
                    val left = qrCode.getOrNull(x - 1)?.getOrNull(y) == true

                    if (right && bottom && qrCode.getOrNull(x + 1)?.getOrNull(y + 1) == true) {
                        bottomRight = moduleCornerRadius
                    }

                    if (left && bottom && qrCode.getOrNull(x - 1)?.getOrNull(y + 1) == true) {
                        bottomLeft = moduleCornerRadius
                    }

                    if (right && top && qrCode.getOrNull(x + 1)?.getOrNull(y - 1) == true) {
                        topRight = moduleCornerRadius
                    }

                    if (left && top && qrCode.getOrNull(x - 1)?.getOrNull(y - 1) == true) {
                        topLeft = moduleCornerRadius
                    }

                    canvas.drawRect(rect, foregroundPaint)
                    val radii = floatArrayOf(
                        topLeft, topLeft,
                        topRight, topRight,
                        bottomRight, bottomRight,
                        bottomLeft, bottomLeft
                    )
                    val roundPath = Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
                    canvas.drawPath(roundPath, backgroundPaint)
                }
            }
        }

        return bitmap
    }

    fun renderNoise(
        qrSidePixels: Int = defaultSidePixels(),
        dynamic: Boolean,
        relativePadding: Float = defaultRelativePadding(),
        bgRoundingPixels: Float = defaultBgRounding()
    ): Bitmap {
        val colors = getQrColors(dynamic)
        return renderNoise(
            qrSidePixels,
            colors.first,
            colors.second,
            relativePadding,
            bgRoundingPixels
        )
    }

    fun renderNoise(
        qrSidePixels: Int = defaultSidePixels(),
        backgroundColor: Int,
        foregroundColor: Int,
        relativePadding: Float = defaultRelativePadding(),
        bgRoundingPixels: Float = defaultBgRounding()
    ): Bitmap {
        val bitmap = createBitmap(qrSidePixels, qrSidePixels)
        val canvas = Canvas(bitmap)

        val paletteSize = 7

        val palette = generateColorPalette(backgroundColor, foregroundColor, paletteSize)
            .map { Paint().apply { color = it; isAntiAlias = true } }

        val backgroundPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
        }

        val bg = Path().apply {
            addRoundRect(
                RectF(0F, 0F, qrSidePixels.toFloat(), qrSidePixels.toFloat()),
                bgRoundingPixels,
                bgRoundingPixels,
                Path.Direction.CW
            )
        }

        canvas.drawPath(bg, backgroundPaint)

        val clip = Path().apply {
            addRoundRect(
                RectF(
                    qrSidePixels * relativePadding,
                    qrSidePixels * relativePadding,
                    qrSidePixels.toFloat() * (1 - relativePadding),
                    qrSidePixels.toFloat() * (1 - relativePadding)
                ),
                bgRoundingPixels / 1.5F,
                bgRoundingPixels / 1.5F,
                Path.Direction.CW
            )
        }


        canvas.clipPath(clip)

        val paddingPixels = qrSidePixels * relativePadding / 2
        val numberOfSquares = 1000
        val minSquareRelSide = 0.02
        val maxSquareRelSide = 0.03

        val random = Random.Default
        for (i in 1..numberOfSquares) {
            val side =
                (random.nextDouble(minSquareRelSide, maxSquareRelSide) * qrSidePixels).toFloat()
            val centerPadding = maxSquareRelSide * qrSidePixels + paddingPixels
            val centerX = random.nextDouble(centerPadding, qrSidePixels - centerPadding).toFloat()
            val centerY = random.nextDouble(centerPadding, qrSidePixels - centerPadding).toFloat()
            val color = palette[random.nextInt(paletteSize - 2) + 1]
            val shape = Path().apply {
                addRoundRect(
                    centerX - side / 2,
                    centerY - side / 2,
                    centerX + side / 2,
                    centerY + side / 2,
                    side / 3,
                    side / 3,
                    Path.Direction.CW
                )
            }
            canvas.drawPath(shape, color)
        }

        return bitmap
    }

    fun generateColorPalette(startColor: Int, endColor: Int, count: Int): List<Int> {
        val palette = mutableListOf<Int>()
        val evaluator = ArgbEvaluator()
        for (i in 0 until count) {
            val fraction = i.toFloat() / (count - 1)
            val color = evaluator.evaluate(fraction, startColor, endColor) as Int
            palette.add(color)
        }
        return palette
    }

    fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    fun defaultBgRounding(): Float {
        return dpToPx(16F)
    }

    fun defaultSidePixels(): Int {
        return 420
    }

    fun defaultRelativePadding(): Float {
        return 0.05F
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