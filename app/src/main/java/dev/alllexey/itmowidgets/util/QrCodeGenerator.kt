package dev.alllexey.itmowidgets.util

import io.nayuki.qrcodegen.QrCode
import io.nayuki.qrcodegen.QrSegment
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors
import java.util.stream.IntStream

class QrCodeGenerator {

    fun generate(hex: String): QrCode {
        val segment = QrSegment.makeBytes(hex.toByteArray(StandardCharsets.ISO_8859_1))
        return QrCode.encodeSegments(listOf(segment), QrCode.Ecc.LOW, 1, 1, -1, false)
    }

    fun toBooleans(qrCode: QrCode): List<List<Boolean>> {
        return IntStream.range(0, qrCode.size)
            .mapToObj { i ->
                IntStream.range(0, qrCode.size)
                    .mapToObj { j -> qrCode.getModule(i, j) }
                    .collect(Collectors.toList())
            }
            .collect(Collectors.toList())
    }
}