package dev.alllexey.itmowidgets.feature.recordbook.domain

import kotlin.math.abs

/**
 * Points as the Russian `NumberFormat` with at most one fraction digit wrote them: `48,5`, `72`, `1 234,5` with a
 * no-break space between thousands. Rounding is HALF_EVEN on the double's exact binary value, as `DecimalFormat`
 * does: `0.05` (a little above) gives `0,1`, `0.25` (exact) gives `0,2`, `0.35` (a little below) gives `0,3`.
 */
fun formatRecordbookNumber(value: Double): String {
    if (!value.isFinite()) return value.toString()
    val sign = if (value < 0 || (value == 0.0 && 1 / value < 0)) "-" else ""
    val tenths = roundedTenths(abs(value))
    val whole = groupThousands((tenths / 10).toString())
    val fraction = tenths % 10
    return if (fraction == 0L) "$sign$whole" else "$sign$whole$DECIMAL_SEPARATOR$fraction"
}

/** `value * 10` rounded half to even, decided on the exact value `mantissa * 2^exponent`, never on a rounded product. */
private fun roundedTenths(value: Double): Long {
    if (value >= EXACT_LIMIT) return value.toLong() * 10
    val bits = value.toRawBits()
    val biased = ((bits ushr MANTISSA_BITS) and EXPONENT_MASK).toInt()
    val fraction = bits and MANTISSA_MASK
    // A normal double is (2^52 + fraction) * 2^(biased - 1075); a subnormal one fraction * 2^-1074.
    val mantissa = if (biased == 0) fraction else fraction or IMPLICIT_BIT
    val exponent = if (biased == 0) MIN_EXPONENT else biased - EXPONENT_BIAS
    val scaled = mantissa * 10
    if (exponent >= 0) return scaled shl exponent
    val shift = -exponent
    // Below 2^-60 the value is under a twentieth of a point: it rounds to zero tenths.
    if (shift > MAX_SHIFT) return 0
    val whole = scaled ushr shift
    val rest = scaled and ((1L shl shift) - 1)
    val half = 1L shl (shift - 1)
    return when {
        rest > half -> whole + 1
        rest < half -> whole
        whole % 2 == 0L -> whole
        else -> whole + 1
    }
}

private fun groupThousands(digits: String): String =
    digits.reversed().chunked(GROUP_SIZE).joinToString(GROUP_SEPARATOR).reversed()

private const val DECIMAL_SEPARATOR = ","
private const val GROUP_SEPARATOR = "\u00A0"
private const val GROUP_SIZE = 3
private const val MANTISSA_BITS = 52
private const val EXPONENT_MASK = 0x7FFL
private const val MANTISSA_MASK = (1L shl MANTISSA_BITS) - 1
private const val IMPLICIT_BIT = 1L shl MANTISSA_BITS
private const val EXPONENT_BIAS = 1075
private const val MIN_EXPONENT = -1074
private const val MAX_SHIFT = 60

/** From 2^52 on every double is whole; ten times a score still fits a Long. */
private const val EXACT_LIMIT = 4.503599627370496E15
