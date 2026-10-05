/*
 * QrCode below is a byte-mode, version 1, error correction L port of
 * QR Code generator library (Java) 1.8.0
 *
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */

package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import kotlin.math.abs

/** Stateless; `:app` provides it in `di/QrModule.kt`. */
class QrCodeGenerator {

    /**
     * Encodes [hex] one byte per char (ISO-8859-1) into the version 1 code the pass screen, widgets and tiles draw.
     * Throws [IllegalArgumentException] above [QrCode.MAX_BYTES] bytes.
     */
    fun generate(hex: String): QrCode = QrCode.encodeBytes(hex.toLatin1Bytes())

    /** The outer index is x (column) and the inner one y (row), as the renderers read it. */
    fun toBooleans(qrCode: QrCode): List<List<Boolean>> =
        List(qrCode.size) { x -> List(qrCode.size) { y -> qrCode.getModule(x, y) } }

    private fun String.toLatin1Bytes(): ByteArray =
        ByteArray(length) { i -> this[i].code.let { if (it <= 0xFF) it else '?'.code }.toByte() }
}

/**
 * A version 1, error correction L QR code with one byte-mode segment and the lowest-penalty mask: module for module
 * what io.nayuki:qrcodegen built with `encodeSegments(listOf(makeBytes(data)), LOW, 1, 1, -1, false)`. The golden
 * matrices in the unit test resources `qr/` pin that parity.
 */
class QrCode private constructor(dataCodewords: ByteArray) {

    val version: Int = VERSION
    val size: Int = SIZE
    val mask: Int

    private val modules = Array(SIZE) { BooleanArray(SIZE) }
    private val isFunction = Array(SIZE) { BooleanArray(SIZE) }

    init {
        drawFunctionPatterns()
        drawCodewords(dataCodewords + reedSolomonRemainder(dataCodewords, reedSolomonDivisor(ECC_CODEWORDS)))
        mask = (0 until MASK_COUNT).minBy(::penaltyWithMask)
        applyMask(mask)
        drawFormatBits(mask)
    }

    fun getModule(x: Int, y: Int): Boolean = x in 0 until SIZE && y in 0 until SIZE && modules[y][x]

    private fun drawFunctionPatterns() {
        for (i in 0 until SIZE) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }
        drawFinderPattern(3, 3)
        drawFinderPattern(SIZE - 4, 3)
        drawFinderPattern(3, SIZE - 4)
        drawFormatBits(0)
    }

    private fun drawFinderPattern(x: Int, y: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val distance = maxOf(abs(dx), abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until SIZE && yy in 0 until SIZE) setFunctionModule(xx, yy, distance != 2 && distance != 4)
            }
        }
    }

    private fun drawFormatBits(mask: Int) {
        val data = ECC_LOW_FORMAT_BITS shl 3 or mask
        var remainder = data
        repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
        val bits = (data shl 10 or remainder) xor 0x5412

        for (i in 0..5) setFunctionModule(8, i, bits.bit(i))
        setFunctionModule(8, 7, bits.bit(6))
        setFunctionModule(8, 8, bits.bit(7))
        setFunctionModule(7, 8, bits.bit(8))
        for (i in 9 until 15) setFunctionModule(14 - i, 8, bits.bit(i))

        for (i in 0 until 8) setFunctionModule(SIZE - 1 - i, 8, bits.bit(i))
        for (i in 8 until 15) setFunctionModule(8, SIZE - 15 + i, bits.bit(i))
        setFunctionModule(8, SIZE - 8, true)
    }

    private fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
        modules[y][x] = isDark
        isFunction[y][x] = true
    }

    private fun drawCodewords(codewords: ByteArray) {
        val bitCount = codewords.size * 8
        var i = 0
        var right = SIZE - 1
        while (right >= 1) {
            if (right == 6) right = 5
            val upward = (right + 1) and 2 == 0
            for (vertical in 0 until SIZE) {
                for (j in 0..1) {
                    val x = right - j
                    val y = if (upward) SIZE - 1 - vertical else vertical
                    if (!isFunction[y][x] && i < bitCount) {
                        modules[y][x] = (codewords[i ushr 3].toInt() and 0xFF).bit(7 - (i and 7))
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    /** XOR, so a second call with the same mask undoes the first. */
    private fun applyMask(mask: Int) {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    else -> throw IllegalArgumentException("Mask $mask is out of range")
                }
                modules[y][x] = modules[y][x] xor (invert && !isFunction[y][x])
            }
        }
    }

    private fun penaltyWithMask(mask: Int): Int {
        applyMask(mask)
        drawFormatBits(mask)
        val penalty = penaltyScore()
        applyMask(mask)
        return penalty
    }

    private fun penaltyScore(): Int {
        var result = 0
        for (y in 0 until SIZE) result += linePenalty { x -> modules[y][x] }
        for (x in 0 until SIZE) result += linePenalty { y -> modules[y][x] }

        for (y in 0 until SIZE - 1) {
            for (x in 0 until SIZE - 1) {
                val color = modules[y][x]
                if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) {
                    result += PENALTY_N2
                }
            }
        }

        val dark = modules.sumOf { row -> row.count { it } }
        val total = SIZE * SIZE
        val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
        return result + k * PENALTY_N4
    }

    private inline fun linePenalty(moduleAt: (Int) -> Boolean): Int {
        var result = 0
        var runColor = false
        var runLength = 0
        val runHistory = IntArray(7)
        for (i in 0 until SIZE) {
            if (moduleAt(i) == runColor) {
                runLength++
                if (runLength == 5) result += PENALTY_N1 else if (runLength > 5) result++
            } else {
                addRunToHistory(runLength, runHistory)
                if (!runColor) result += countFinderLikePatterns(runHistory) * PENALTY_N3
                runColor = moduleAt(i)
                runLength = 1
            }
        }
        return result + terminateAndCountFinderLikePatterns(runColor, runLength, runHistory) * PENALTY_N3
    }

    private fun countFinderLikePatterns(runHistory: IntArray): Int {
        val n = runHistory[1]
        val core = n > 0 && runHistory[2] == n && runHistory[3] == n * 3 && runHistory[4] == n && runHistory[5] == n
        return (if (core && runHistory[0] >= n * 4 && runHistory[6] >= n) 1 else 0) +
            (if (core && runHistory[6] >= n * 4 && runHistory[0] >= n) 1 else 0)
    }

    private fun terminateAndCountFinderLikePatterns(runColor: Boolean, runLength: Int, runHistory: IntArray): Int {
        var length = runLength
        if (runColor) {
            addRunToHistory(length, runHistory)
            length = 0
        }
        addRunToHistory(length + SIZE, runHistory)
        return countFinderLikePatterns(runHistory)
    }

    /** The light border counts toward the first run of a line. */
    private fun addRunToHistory(runLength: Int, runHistory: IntArray) {
        val length = if (runHistory[0] == 0) runLength + SIZE else runLength
        runHistory.copyInto(runHistory, destinationOffset = 1, startIndex = 0, endIndex = runHistory.size - 1)
        runHistory[0] = length
    }

    companion object {
        /** Byte-mode capacity of a version 1, error correction L code. */
        const val MAX_BYTES = 17

        private const val VERSION = 1
        private const val SIZE = VERSION * 4 + 17
        private const val DATA_CODEWORDS = 19
        private const val ECC_CODEWORDS = 7
        private const val ECC_LOW_FORMAT_BITS = 1
        private const val BYTE_MODE_BITS = 0x4
        private const val MASK_COUNT = 8

        private const val PENALTY_N1 = 3
        private const val PENALTY_N2 = 3
        private const val PENALTY_N3 = 40
        private const val PENALTY_N4 = 10

        fun encodeBytes(data: ByteArray): QrCode {
            val capacityBits = DATA_CODEWORDS * 8
            require(data.size <= MAX_BYTES) {
                "Data length = ${4 + 8 + data.size * 8} bits, Max capacity = $capacityBits bits"
            }

            val codewords = ByteArray(DATA_CODEWORDS)
            var bitLength = 0
            fun appendBits(value: Int, length: Int) {
                for (i in length - 1 downTo 0) {
                    if (value.bit(i)) {
                        val index = bitLength ushr 3
                        codewords[index] = (codewords[index].toInt() or (0x80 ushr (bitLength and 7))).toByte()
                    }
                    bitLength++
                }
            }

            appendBits(BYTE_MODE_BITS, 4)
            appendBits(data.size, 8)
            data.forEach { appendBits(it.toInt() and 0xFF, 8) }
            appendBits(0, minOf(4, capacityBits - bitLength))
            appendBits(0, (8 - bitLength % 8) % 8)
            var padByte = 0xEC
            while (bitLength < capacityBits) {
                appendBits(padByte, 8)
                padByte = padByte xor 0xEC xor 0x11
            }
            return QrCode(codewords)
        }

        private fun reedSolomonDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in result.indices) {
                    var value = reedSolomonMultiply(result[j].toInt() and 0xFF, root)
                    if (j + 1 < result.size) value = value xor (result[j + 1].toInt() and 0xFF)
                    result[j] = value.toByte()
                }
                root = reedSolomonMultiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (byte in data) {
                val factor = (byte.toInt() xor result[0].toInt()) and 0xFF
                result.copyInto(result, destinationOffset = 0, startIndex = 1)
                result[result.size - 1] = 0
                for (i in result.indices) {
                    val term = reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor)
                    result[i] = (result[i].toInt() xor term).toByte()
                }
            }
            return result
        }

        private fun reedSolomonMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun Int.bit(index: Int): Boolean = (this ushr index) and 1 != 0
    }
}
