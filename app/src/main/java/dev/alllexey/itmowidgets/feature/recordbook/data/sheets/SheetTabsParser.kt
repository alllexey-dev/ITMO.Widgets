package dev.alllexey.itmowidgets.feature.recordbook.data.sheets

import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab

/** The tabs of a sheet from the JavaScript of its HTML view: `items.push({name: "…", pageUrl: "…", gid: "…"`. */
internal object SheetTabsParser {
    private const val STRING = """"((?:[^"\\]|\\.)*)""""
    private val ITEM = Regex("""items\.push\(\{\s*name:\s*$STRING,\s*pageUrl:\s*$STRING,\s*gid:\s*$STRING""")

    fun parse(html: String): List<SheetTab> = ITEM.findAll(html)
        .mapNotNull { match ->
            val gid = unescape(match.groupValues[3]).toLongOrNull() ?: return@mapNotNull null
            SheetTab(gid, unescape(match.groupValues[1]))
        }
        .distinctBy { it.gid }
        .toList()

    /** JavaScript string escapes: an escaped character, a line break, two- and four-digit hex codes. */
    private fun unescape(text: String): String {
        val result = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char != '\\' || index + 1 >= text.length) {
                result.append(char); index++; continue
            }
            val next = text[index + 1]
            when (next) {
                'n' -> { result.append('\n'); index += 2 }
                't' -> { result.append('\t'); index += 2 }
                'r' -> { result.append('\r'); index += 2 }
                'x' -> index += hex(text, index + 2, 2, result)
                'u' -> index += hex(text, index + 2, 4, result)
                else -> { result.append(next); index += 2 }
            }
        }
        return result.toString()
    }

    /** Appends the character of [digits] hex digits at [start]; returns how far to move past the backslash. */
    private fun hex(text: String, start: Int, digits: Int, result: StringBuilder): Int {
        val code = text.substring(start, minOf(text.length, start + digits))
            .takeIf { it.length == digits }?.toIntOrNull(16)
        return if (code != null) {
            result.append(code.toChar()); 2 + digits
        } else {
            result.append(text[start - 1]); 2
        }
    }
}
