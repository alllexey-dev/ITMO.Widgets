package dev.alllexey.itmowidgets.core.url

/**
 * Percent-encoding in UTF-8 with the output of the JDK `URLEncoder` and `URLDecoder`, so links the app builds stay
 * byte-identical: `A-Z a-z 0-9 . - * _` pass through, every other byte becomes an upper-case `%XX`, and an unpaired
 * surrogate is encoded as `?` (`%3F`).
 */
object UrlEncoding {

    /** `application/x-www-form-urlencoded`: a space becomes `+`. */
    fun formEncode(value: String): String = encode(value, space = "+")

    /** The same set with a space as `%20`, for URI components that do not read `+` as a space, such as `geo:`. */
    fun percentEncode(value: String): String = encode(value, space = "%20")

    /** The inverse of [formEncode]: `+` is a space; null for a `%` without two hex digits after it. */
    fun formDecode(value: String): String? = if (hasOnlyValidEscapes(value)) decode(value, plusIsSpace = true) else null

    /** Decodes escapes a [StrictUri] component has already been checked to hold; `+` stays `+`. */
    internal fun decodeValidatedEscapes(value: String): String = decode(value, plusIsSpace = false)

    private fun encode(value: String, space: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                isUnreserved(char) -> append(char).also { index++ }
                char == ' ' -> append(space).also { index++ }
                else -> {
                    val codePoint = codePointAt(value, index)
                    index += if (codePoint > 0xFFFF) 2 else 1
                    utf8(codePoint).forEach { byte -> appendEscape(byte) }
                }
            }
        }
    }

    private fun isUnreserved(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' ||
        char == '.' || char == '-' || char == '*' || char == '_'

    /** The code point at [index]; an unpaired surrogate reads as `?`, as `String.getBytes(UTF_8)` writes it. */
    private fun codePointAt(value: String, index: Int): Int {
        val char = value[index]
        val next = value.getOrNull(index + 1)
        return when {
            char.isHighSurrogate() && next != null && next.isLowSurrogate() ->
                0x10000 + ((char.code - 0xD800) shl 10) + (next.code - 0xDC00)
            char.isSurrogate() -> '?'.code
            else -> char.code
        }
    }

    private fun utf8(codePoint: Int): IntArray = when {
        codePoint < 0x80 -> intArrayOf(codePoint)
        codePoint < 0x800 -> intArrayOf(0xC0 or (codePoint shr 6), 0x80 or (codePoint and 0x3F))
        codePoint < 0x10000 -> intArrayOf(
            0xE0 or (codePoint shr 12), 0x80 or ((codePoint shr 6) and 0x3F), 0x80 or (codePoint and 0x3F),
        )
        else -> intArrayOf(
            0xF0 or (codePoint shr 18), 0x80 or ((codePoint shr 12) and 0x3F),
            0x80 or ((codePoint shr 6) and 0x3F), 0x80 or (codePoint and 0x3F),
        )
    }

    private fun StringBuilder.appendEscape(byte: Int) {
        append('%').append(HEX[byte shr 4]).append(HEX[byte and 0x0F])
    }

    private fun hasOnlyValidEscapes(value: String): Boolean =
        value.indices.all { value[it] != '%' || UriChars.isEscape(value, it) }

    /** Each run of escapes is decoded as one UTF-8 sequence; malformed bytes become U+FFFD. */
    private fun decode(value: String, plusIsSpace: Boolean): String {
        if ('%' !in value && !(plusIsSpace && '+' in value)) return value
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char == '%' -> {
                    val bytes = ArrayList<Byte>()
                    while (index < value.length && value[index] == '%') {
                        bytes += value.substring(index + 1, index + 3).toInt(16).toByte()
                        index += 3
                    }
                    out.append(bytes.toByteArray().decodeToString())
                }
                char == '+' && plusIsSpace -> out.append(' ').also { index++ }
                else -> out.append(char).also { index++ }
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
