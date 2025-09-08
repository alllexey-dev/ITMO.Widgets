package me.alllexey123.itmowidgets.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment
import java.nio.charset.StandardCharsets
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

object QrCodeProvider {

    fun qrCodeToBitmap(qrCode: QrCode, qrSide: Int, pixelsPerModule: Int): Bitmap {
        val bitmap = createBitmap(qrSide * pixelsPerModule, qrSide * pixelsPerModule, Bitmap.Config.RGB_565)
        for (x in 0 until qrSide * pixelsPerModule) {
            for (y in 0 until qrSide * pixelsPerModule) {
                bitmap[x, y] = if (qrCode.getModule(
                        x / pixelsPerModule,
                        y / pixelsPerModule
                    )
                ) Color.BLACK else Color.WHITE
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
            val qrCode = QrCode.encodeSegments(listOf(segment), QrCode.Ecc.LOW, 1, 1, 5, false)

            return qrCode
        } catch (e: Exception) {
            throw RuntimeException("Could not get QR code", e)
        }
    }
}