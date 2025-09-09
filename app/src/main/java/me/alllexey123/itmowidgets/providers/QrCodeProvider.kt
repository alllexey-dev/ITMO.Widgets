package me.alllexey123.itmowidgets.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment
import java.nio.charset.StandardCharsets
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

object QrCodeProvider {

    fun emptyQrCode(side: Int, rounding: Float, fillColor: Int): Bitmap {
        val bitmap = createBitmap(side, side, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = fillColor
            isAntiAlias = true
        }

        val path = Path().apply {
            addRoundRect(RectF(0F, 0F, side.toFloat(), side.toFloat()), rounding, rounding, Path.Direction.CW)
        }

        canvas.drawPath(path, paint)

        return bitmap
    }

    fun qrCodeToBitmap(qrCode: QrCode, qrSide: Int, pixelsPerModule: Int): Bitmap {
        val bitmap = createBitmap(qrSide * pixelsPerModule, qrSide * pixelsPerModule)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val black = Paint().apply {
            color = "#FF222222".toColorInt()
            isAntiAlias = true
        }

        val white = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        val cornerRadius = pixelsPerModule * 0.5f

        // draw the black modules
        for (x in 0 until qrSide) {
            for (y in 0 until qrSide) {
                if (qrCode.getModule(x, y)) {
                    val left = (x * pixelsPerModule).toFloat()
                    val top = (y * pixelsPerModule).toFloat()
                    val right = left + pixelsPerModule
                    val bottom = top + pixelsPerModule

                    val topN = qrCode.getModule(x, y - 1)
                    val bottomN = qrCode.getModule(x, y + 1)
                    val leftN = qrCode.getModule(x - 1, y)
                    val rightN = qrCode.getModule(x + 1, y)

                    val radii = floatArrayOf(
                        if (!topN && !leftN) cornerRadius else 0f,
                        if (!topN && !leftN) cornerRadius else 0f,

                        if (!topN && !rightN) cornerRadius else 0f,
                        if (!topN && !rightN) cornerRadius else 0f,

                        if (!bottomN && !rightN) cornerRadius else 0f,
                        if (!bottomN && !rightN) cornerRadius else 0f,

                        if (!bottomN && !leftN) cornerRadius else 0f,
                        if (!bottomN && !leftN) cornerRadius else 0f
                    )

                    val path = Path().apply {
                        addRoundRect(RectF(left, top, right, bottom), radii, Path.Direction.CW)
                    }
                    canvas.drawPath(path, black)
                }
            }
        }

        // redraw the white modules (round corners)
        for (x in 0 until qrSide) {
            for (y in 0 until qrSide) {
                if (!qrCode.getModule(x, y)) {
                    val x1 = (x * pixelsPerModule).toFloat()
                    val y1 = (y * pixelsPerModule).toFloat()
                    val x2 = x1 + pixelsPerModule
                    val y2 = y1 + pixelsPerModule
                    val rect = RectF(x1, y1, x2, y2)

                    var bottomRight = 0f
                    var bottomLeft = 0f
                    var topLeft = 0f
                    var topRight = 0f

                    val top = qrCode.getModule(x, y - 1)
                    val right = qrCode.getModule(x + 1, y)
                    val bottom = qrCode.getModule(x, y + 1)
                    val left = qrCode.getModule(x - 1, y)

                    if (right && bottom && qrCode.getModule(x + 1, y + 1)) {
                        bottomRight = cornerRadius
                    }

                    if (left && bottom && qrCode.getModule(x - 1, y + 1)) {
                        bottomLeft = cornerRadius
                    }

                    if (right && top && qrCode.getModule(x + 1, y - 1)) {
                        topRight = cornerRadius
                    }

                    if (left && top && qrCode.getModule(x - 1, y - 1)) {
                        topLeft = cornerRadius
                    }

                    canvas.drawRect(rect, black)
                    val radii = floatArrayOf(
                        topLeft, topLeft,
                        topRight, topRight,
                        bottomRight, bottomRight,
                        bottomLeft, bottomLeft
                    )
                    val roundPath = Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
                    canvas.drawPath(roundPath, white)
                }
            }
        }

        return bitmap
    }


    fun getQrCode(context: Context): QrCode {
        val myItmo = MyItmoProvider.getMyItmo(context)

        try {
            val simpleResponse = myItmo.api().getQrCode().execute().body()

            if (simpleResponse == null) {
                throw RuntimeException("QR code response body is null")
            }

            if (simpleResponse.response == null) {
                throw RuntimeException("QR code wrapper is null")
            }

            if (simpleResponse.response.qrHex == null) {
                throw RuntimeException("QR code is null")
            }

            val qrHex = simpleResponse.response.qrHex
            val segment = QrSegment.makeBytes(qrHex.toByteArray(StandardCharsets.ISO_8859_1))
            val qrCode = QrCode.encodeSegments(listOf(segment), QrCode.Ecc.LOW, 1, 1, -1, false)

            return qrCode
        } catch (e: Exception) {
            throw RuntimeException("Could not get QR code", e)
        }
    }
}