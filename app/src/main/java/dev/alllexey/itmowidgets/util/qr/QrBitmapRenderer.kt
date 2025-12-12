package dev.alllexey.itmowidgets.util.qr

import android.animation.ArgbEvaluator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt
import kotlin.random.Random

class QrBitmapRenderer() {

    fun renderEmpty(
        qrSidePixels: Int,
        color: Int,
        relativeRounding: Float
    ): Bitmap {
        val bitmap = createBitmap(qrSidePixels, qrSidePixels)
        val canvas = Canvas(bitmap)

        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }

        val bg = Path().apply {
            addRoundRect(
                RectF(0F, 0F, qrSidePixels.toFloat(), qrSidePixels.toFloat()),
                relativeRounding * qrSidePixels,
                relativeRounding * qrSidePixels,
                Path.Direction.CW
            )
        }
        canvas.drawPath(bg, paint)

        return bitmap
    }

    // padding: [0, 0.5]
    fun render(
        qrCode: List<List<Boolean>>,
        qrSidePixels: Int,
        backgroundColor: Int,
        foregroundColor: Int,
        relativePadding: Float,
        relativeRounding: Float
    ): Bitmap {
        val qrSize = qrCode.size
        val bitmap = createBitmap(qrSidePixels, qrSidePixels)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint().apply {
            color = backgroundColor
            isAntiAlias = true
        }

        val foregroundPaint = Paint().apply {
            color = foregroundColor
            isAntiAlias = true
        }

        val rawModuleSize = qrSidePixels * (1 - relativePadding * 2) / qrSize
        val moduleSidePixels = rawModuleSize.roundToInt().toFloat()
        val totalContentSize = moduleSidePixels * qrSize
        val paddingPixels = (qrSidePixels - totalContentSize) / 2f

        val bg = Path().apply {
            addRoundRect(
                RectF(0F, 0F, qrSidePixels.toFloat(), qrSidePixels.toFloat()),
                relativeRounding * qrSidePixels,
                relativeRounding * qrSidePixels,
                Path.Direction.CW
            )
        }
        canvas.drawPath(bg, backgroundPaint)

        val moduleCornerRadius = moduleSidePixels * 0.5f
        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                if (qrCode[x][y]) {
                    val left = (x * moduleSidePixels + paddingPixels).roundToInt().toFloat()
                    val top = (y * moduleSidePixels + paddingPixels).roundToInt().toFloat()
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

        for (x in 0 until qrSize) {
            for (y in 0 until qrSize) {
                if (!qrCode[x][y]) {
                    val left = (x * moduleSidePixels + paddingPixels).roundToInt().toFloat()
                    val top = (y * moduleSidePixels + paddingPixels).roundToInt().toFloat()
                    val right = left + moduleSidePixels
                    val bottom = top + moduleSidePixels
                    val rect = RectF(left, top, right, bottom)

                    val topN = qrCode.getOrNull(x)?.getOrNull(y - 1) == true
                    val bottomN = qrCode.getOrNull(x)?.getOrNull(y + 1) == true
                    val leftN = qrCode.getOrNull(x - 1)?.getOrNull(y) == true
                    val rightN = qrCode.getOrNull(x + 1)?.getOrNull(y) == true

                    var bottomRight = 0f
                    var bottomLeft = 0f
                    var topLeft = 0f
                    var topRight = 0f

                    if (rightN && bottomN && qrCode.getOrNull(x + 1)?.getOrNull(y + 1) == true) {
                        bottomRight = moduleCornerRadius
                    }
                    if (leftN && bottomN && qrCode.getOrNull(x - 1)?.getOrNull(y + 1) == true) {
                        bottomLeft = moduleCornerRadius
                    }
                    if (rightN && topN && qrCode.getOrNull(x + 1)?.getOrNull(y - 1) == true) {
                        topRight = moduleCornerRadius
                    }
                    if (leftN && topN && qrCode.getOrNull(x - 1)?.getOrNull(y - 1) == true) {
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


    // padding: [0, 0.5]
    fun renderNoise(
        qrSidePixels: Int,
        backgroundColor: Int,
        foregroundColor: Int,
        relativePadding: Float,
        relativeRounding: Float
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
                relativeRounding * qrSidePixels,
                relativeRounding * qrSidePixels,
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
                relativeRounding * qrSidePixels / 1.5F,
                relativeRounding * qrSidePixels / 1.5F,
                Path.Direction.CW
            )
        }


        canvas.clipPath(clip)

        val paddingPixels = qrSidePixels * relativePadding / 2
        val numberOfSquares = 1000
        val minSquareRelSide = 0.02
        val maxSquareRelSide = 0.03

        val random = Random(System.currentTimeMillis() / 1000 / 60 / 60)
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
}