package dev.alllexey.itmowidgets.feature.recordbook.domain.sheets

import java.util.Locale

/** One normalisation for every cell, name and header match in a sheet. */
object SheetText {
    private val SPACES = Regex("""\s+""")
    private val AFTER_DOT = Regex("""\.\s+""")
    private val SEPARATORS = Regex("""[^\p{L}\p{N}Σ∑]+""")

    /** Case, `ё`, non-breaking and repeated spaces; «Тестов Т. Т.» and «Тестов Т.Т.» are the same text. */
    fun normalize(text: String): String = text
        .replace('\u00A0', ' ')
        .trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(SPACES, " ")
        .replace(AFTER_DOT, ".")

    /** The words of [text] after [normalize]; `σ` and `∑` count as words. */
    fun tokens(text: String): List<String> = normalize(text).split(SEPARATORS).filter { it.isNotEmpty() }

    /** [text] on one line with single spaces, as a header or a value is shown. */
    fun compact(text: String): String = text.replace('\u00A0', ' ').replace(SPACES, " ").trim()
}
