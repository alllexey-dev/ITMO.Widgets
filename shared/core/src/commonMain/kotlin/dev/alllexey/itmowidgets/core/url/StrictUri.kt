package dev.alllexey.itmowidgets.core.url

/**
 * A URI reference read by the strict RFC 2396 grammar the JDK `URI` class applies, together with its two deviations:
 * bracketed IPv6 literals (RFC 2732) and an empty authority before a path, query or fragment. The link and WebView
 * policies gate navigation on it, so it must refuse what the JVM parser refused: a space, a backslash, a malformed
 * escape or a second `#` makes [parse] return null. Ktor's `Url` repairs such input and must not replace it here.
 *
 * [host] is set only for a server-based authority, `[userinfo@]host[:port]` with a DNS name, a dotted IPv4 address or
 * an IPv6 literal (kept with its brackets). Any other authority, such as a Unicode IDN, an underscore in a name or a
 * port beyond [Int.MAX_VALUE], still parses as a registry name, as on the JVM, but then [rawUserInfo], [host] and
 * [port] are null, so every host check refuses it.
 */
class StrictUri private constructor(
    /** As written, case included; null for a relative reference. */
    val scheme: String?,
    /** Empty, not null, for an `@` with nothing before it. */
    val rawUserInfo: String?,
    val host: String?,
    /** Null when the authority has no port or an empty one. */
    val port: Int?,
    /** Empty for `https://host`; null only for an opaque URI such as `mailto:` or `javascript:`. */
    val rawPath: String?,
    val rawQuery: String?,
    val rawFragment: String?,
) {
    /** [rawPath] with its percent escapes decoded as UTF-8; `+` stays `+`. */
    val path: String?
        get() = rawPath?.let(UrlEncoding::decodeValidatedEscapes)

    companion object {
        /** Null where the JDK `URI` constructor throws. */
        fun parse(text: String): StrictUri? {
            val firstDelimiter = text.indexOfFirst { it == ':' || it == '/' || it == '?' || it == '#' }
            if (firstDelimiter < 0 || text[firstDelimiter] != ':') return hierarchical(scheme = null, text, start = 0)
            val scheme = text.substring(0, firstDelimiter)
            if (!isScheme(scheme)) return null
            val rest = firstDelimiter + 1
            return if (text.startsWith("/", rest)) hierarchical(scheme, text, rest) else opaque(scheme, text, rest)
        }

        private fun opaque(scheme: String, text: String, start: Int): StrictUri? {
            val hash = text.indexOf('#', start).takeIf { it >= 0 } ?: text.length
            val part = text.substring(start, hash)
            if (part.isEmpty() || !UriChars.URIC.matchesAll(part)) return null
            val fragment = fragment(text, hash) ?: return null
            return StrictUri(scheme, null, null, null, null, null, fragment.value)
        }

        private fun hierarchical(scheme: String?, text: String, start: Int): StrictUri? {
            var position = start
            var authority = Authority.NONE
            if (text.startsWith("//", position)) {
                val authorityStart = position + 2
                val authorityEnd = text.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).takeIf { it >= 0 }
                    ?: text.length
                if (authorityEnd > authorityStart) {
                    authority = Authority.parse(text.substring(authorityStart, authorityEnd)) ?: return null
                } else if (authorityEnd == text.length) {
                    return null
                }
                position = authorityEnd
            }
            val pathEnd = text.indexOfAny(charArrayOf('?', '#'), position).takeIf { it >= 0 } ?: text.length
            val path = text.substring(position, pathEnd)
            if (!UriChars.PATH.matchesAll(path)) return null
            position = pathEnd
            var query: String? = null
            if (text.startsWith("?", position)) {
                val queryEnd = text.indexOf('#', position + 1).takeIf { it >= 0 } ?: text.length
                query = text.substring(position + 1, queryEnd)
                if (!UriChars.URIC.matchesAll(query)) return null
                position = queryEnd
            }
            val fragment = fragment(text, position) ?: return null
            return StrictUri(scheme, authority.userInfo, authority.host, authority.port, path, query, fragment.value)
        }

        /** The fragment after the `#` at [hash], when there is one; null when it holds an illegal character. */
        private fun fragment(text: String, hash: Int): Fragment? {
            if (hash >= text.length) return Fragment(null)
            val value = text.substring(hash + 1)
            return if (UriChars.URIC.matchesAll(value)) Fragment(value) else null
        }

        private fun isScheme(text: String): Boolean = text.isNotEmpty() && UriChars.isAsciiLetter(text[0]) &&
            text.all { UriChars.isAsciiLetter(it) || UriChars.isAsciiDigit(it) || it == '+' || it == '-' || it == '.' }
    }

    private class Fragment(val value: String?)

    /** A server-based authority, or one with all three parts null: none, empty or a registry name. */
    private class Authority(val userInfo: String?, val host: String?, val port: Int?) {
        companion object {
            val NONE = Authority(null, null, null)

            fun parse(text: String): Authority? =
                server(text) ?: NONE.takeIf { UriChars.REG_NAME.matchesAll(text) }

            private fun server(text: String): Authority? {
                val at = text.indexOf('@')
                val userInfo = if (at >= 0) text.substring(0, at) else null
                if (userInfo != null && !UriChars.USER_INFO.matchesAll(userInfo)) return null
                val hostStart = at + 1
                val hostEnd = if (text.startsWith("[", hostStart)) {
                    val close = text.indexOf(']', hostStart)
                    if (close < 0 || !HostNames.isIpv6Reference(text.substring(hostStart + 1, close))) return null
                    close + 1
                } else {
                    val colon = text.indexOf(':', hostStart)
                    val end = if (colon >= 0) colon else text.length
                    if (!HostNames.isServerHost(text.substring(hostStart, end))) return null
                    end
                }
                val port = when {
                    hostEnd == text.length -> null
                    text[hostEnd] != ':' -> return null
                    else -> port(text.substring(hostEnd + 1)) ?: return null
                }
                return Authority(userInfo, text.substring(hostStart, hostEnd), port?.value)
            }

            private fun port(text: String): Port? = when {
                text.isEmpty() -> Port(null)
                !text.all(UriChars::isAsciiDigit) -> null
                else -> text.toIntOrNull()?.let(::Port)
            }
        }

        private class Port(val value: Int?)
    }
}

/** The RFC 2396 host forms the JDK `URI` class accepts in a server-based authority. */
private object HostNames {

    fun isServerHost(text: String): Boolean = isIpv4(text) || isHostname(text)

    /**
     * `hostname = *( domainlabel "." ) toplabel [ "." ]`; a lone label may start with a digit, the last of several
     * may not, so `1.2.3` is no host while `123` is.
     */
    private fun isHostname(text: String): Boolean {
        val labels = text.removeSuffix(".").split('.')
        if (!labels.all(::isLabel)) return false
        return labels.size == 1 || UriChars.isAsciiLetter(labels.last()[0])
    }

    private fun isLabel(text: String): Boolean = text.isNotEmpty() && UriChars.isAsciiAlphanumeric(text.first()) &&
        UriChars.isAsciiAlphanumeric(text.last()) && text.all { UriChars.isAsciiAlphanumeric(it) || it == '-' }

    /** Four decimal parts, each at most 255; leading zeros allowed. */
    fun isIpv4(text: String): Boolean {
        val parts = text.split('.')
        return parts.size == 4 && parts.all { part ->
            part.isNotEmpty() && part.all(UriChars::isAsciiDigit) && (part.toIntOrNull() ?: Int.MAX_VALUE) <= 255
        }
    }

    /** The inside of `[...]`: an RFC 4291 address with an optional non-empty `%zone`. */
    fun isIpv6Reference(text: String): Boolean {
        val percent = text.indexOf('%')
        if (percent < 0) return isIpv6(text)
        val zone = text.substring(percent + 1)
        return isIpv6(text.substring(0, percent)) && zone.isNotEmpty() &&
            zone.all { UriChars.isAsciiAlphanumeric(it) || it == '_' || it == '.' }
    }

    /** Eight 16-bit groups, a trailing IPv4 address counting as two; one `::` stands for at least one zero group. */
    private fun isIpv6(text: String): Boolean {
        val gap = text.indexOf("::")
        if (gap < 0) return groupCount(text, allowIpv4 = true) == 8
        if (text.indexOf("::", gap + 1) >= 0) return false
        val head = text.substring(0, gap)
        val tail = text.substring(gap + 2)
        val headGroups = if (head.isEmpty()) 0 else groupCount(head, allowIpv4 = false) ?: return false
        val tailGroups = if (tail.isEmpty()) 0 else groupCount(tail, allowIpv4 = true) ?: return false
        return headGroups + tailGroups < 8
    }

    /** Null when a group is not 1-4 hex digits (or, last and when allowed, an IPv4 address). */
    private fun groupCount(text: String, allowIpv4: Boolean): Int? {
        val groups = text.split(':')
        val last = groups.last()
        val ipv4 = allowIpv4 && '.' in last
        if (ipv4 && !isIpv4(last)) return null
        val hexGroups = if (ipv4) groups.dropLast(1) else groups
        if (!hexGroups.all { it.length in 1..4 && it.all(UriChars::isAsciiHexDigit) }) return null
        return hexGroups.size + if (ipv4) 2 else 0
    }
}

/** RFC 2396 character classes; escapes and visible non-ASCII characters count wherever escapes are allowed. */
internal class UriChars private constructor(private val ascii: String) {

    fun matchesAll(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            index += when {
                isAsciiAlphanumeric(char) || char in ascii -> 1
                char == '%' -> if (isEscape(text, index)) 3 else return false
                isVisibleNonAscii(char) -> 1
                else -> return false
            }
        }
        return true
    }

    companion object {
        private const val MARK = "-_.!~*'()"
        private const val RESERVED = ";/?:@&=+$,[]"

        /** `uric`: query, fragment and opaque part. */
        val URIC = UriChars(RESERVED + MARK)

        /** `pchar`, `;` and `/`. */
        val PATH = UriChars("$MARK:@&=+$,;/")

        val USER_INFO = UriChars("$MARK;:&=+$,")

        val REG_NAME = UriChars("$MARK$,;:@&=+")

        fun isAsciiLetter(char: Char): Boolean = char in 'a'..'z' || char in 'A'..'Z'

        fun isAsciiDigit(char: Char): Boolean = char in '0'..'9'

        fun isAsciiAlphanumeric(char: Char): Boolean = isAsciiLetter(char) || isAsciiDigit(char)

        fun isAsciiHexDigit(char: Char): Boolean = isAsciiDigit(char) || char in 'a'..'f' || char in 'A'..'F'

        fun isEscape(text: String, index: Int): Boolean = index + 2 < text.length &&
            isAsciiHexDigit(text[index + 1]) && isAsciiHexDigit(text[index + 2])

        /** The JVM parser's "other" characters: above U+0080, neither a control nor a space separator. */
        private fun isVisibleNonAscii(char: Char): Boolean = char.code > 0x80 && !char.isISOControl() &&
            char.category != CharCategory.SPACE_SEPARATOR && char.category != CharCategory.LINE_SEPARATOR &&
            char.category != CharCategory.PARAGRAPH_SEPARATOR
    }
}
