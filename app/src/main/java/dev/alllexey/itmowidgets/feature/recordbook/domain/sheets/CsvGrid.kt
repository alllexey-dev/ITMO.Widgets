package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

/**
 * RFC 4180 CSV as Google exports it: quoted fields with `""` for a quote, commas and line breaks inside quotes,
 * `CRLF` or `LF` between records, an optional BOM. Ragged rows are padded by [SheetGrid].
 */
object CsvGrid {

    fun parse(text: String): SheetGrid {
        val source = text.removePrefix("\uFEFF")
        if (source.isEmpty()) return SheetGrid(emptyList())
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (quoted) {
                when {
                    char == '"' && source.getOrNull(index + 1) == '"' -> { field.append('"'); index++ }
                    char == '"' -> quoted = false
                    else -> field.append(char)
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    ',' -> { row += field.toString(); field.clear() }
                    '\r', '\n' -> {
                        if (char == '\r' && source.getOrNull(index + 1) == '\n') index++
                        row += field.toString(); field.clear()
                        rows += row; row = mutableListOf()
                    }
                    else -> field.append(char)
                }
            }
            index++
        }
        val lastEnded = source.last() == '\n' || source.last() == '\r'
        if (!lastEnded || quoted) {
            row += field.toString()
            rows += row
        }
        return SheetGrid(rows)
    }
}
