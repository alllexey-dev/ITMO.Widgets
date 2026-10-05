package dev.alllexey.itmowidgets.buildlogic.strings

/**
 * JSON the way Xcode saves a `.xcstrings` file (checked against `xcstringstool sync` of Xcode 27): keys sorted,
 * 2-space indent, `"key" : value`, `/` and non-ASCII unescaped, no final newline. Opening the project then
 * rewrites nothing.
 */
object XcodeJson {

    /** [value] is a [Map] with [String] keys, a [String] or an [Int]. */
    fun write(value: Any): String = StringBuilder().also { write(it, value, "") }.toString()

    private fun write(out: StringBuilder, value: Any, indent: String) {
        when (value) {
            is Map<*, *> -> {
                val inner = "$indent$INDENT"
                out.append("{\n")
                value.entries.map { (key, item) -> key as String to requireNotNull(item) }.sortedBy { it.first }
                    .forEachIndexed { index, (key, item) ->
                        if (index > 0) out.append(",\n")
                        out.append(inner)
                        string(out, key)
                        out.append(" : ")
                        write(out, item, inner)
                    }
                out.append("\n").append(indent).append('}')
            }
            is String -> string(out, value)
            is Int -> out.append(value)
            else -> throw IllegalArgumentException("Unsupported JSON value ${value::class}")
        }
    }

    private fun string(out: StringBuilder, value: String) {
        out.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    private const val INDENT = "  "
}
