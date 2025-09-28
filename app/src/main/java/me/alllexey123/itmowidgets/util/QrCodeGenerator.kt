package me.alllexey123.itmowidgets.util

import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment
import java.nio.charset.StandardCharsets

class QrCodeGenerator {

    fun generate(hex: String): QrCode {
        val segment = QrSegment.makeBytes(hex.toByteArray(StandardCharsets.ISO_8859_1))
        return QrCode.encodeSegments(listOf(segment), QrCode.Ecc.LOW, 1, 1, -1, false)
    }
}