package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.GradleException

/**
 * `AppSymbol.swift` from `docs/design/icons.tsv`: a Swift-only enum (widget and NSE extensions link no Kotlin) with a
 * case per `shared` and `custom` row. A `custom.*` name is a symbol image in an asset catalog, every other name an
 * SF Symbol.
 */
object AppSymbolSwift {

    fun render(registry: List<String>): String {
        val lines = registry.filter { it.isNotBlank() }
        val header = lines.firstOrNull()?.split('\t').orEmpty()
        val columns = listOf(ID, KIND, SF_SYMBOL).associateWith { header.indexOf(it) }
        columns.filterValues { it < 0 }.keys.firstOrNull()?.let {
            throw GradleException("${AppleExport.ICONS_FILE}: no column $it")
        }
        val symbols = lines.drop(1).map { it.split('\t') }
            .filter { row -> row.getOrNull(columns.getValue(KIND)) in KINDS }
            .map { row ->
                val id = row[columns.getValue(ID)]
                val name = row.getOrNull(columns.getValue(SF_SYMBOL)).orEmpty()
                if (name.isEmpty() || name == "-") {
                    throw GradleException("${AppleExport.ICONS_FILE}: $id has no sf_symbol")
                }
                Symbol(caseName(id), id, name)
            }
            .sortedBy { it.id }
        symbols.groupBy { it.case }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw GradleException("${AppleExport.ICONS_FILE}: two ids give the Swift case $it")
        }
        return buildString {
            append("// Generated from ${AppleExport.ICONS_FILE} by `${AppleExport.COMMAND}`; do not edit.\n")
            append('\n')
            append("/// The icon registry on SwiftUI and system surfaces.\n")
            append("enum AppSymbol: String, CaseIterable {\n")
            symbols.forEach { append("    case ${it.case} = \"${it.id}\"\n") }
            append('\n')
            append("    /// The SF Symbol name, or the asset name of a custom symbol image when `isCustom`.\n")
            append("    var systemName: String {\n")
            append("        switch self {\n")
            symbols.forEach { append("        case .${it.case.trim('`')}: \"${it.name}\"\n") }
            append("        }\n")
            append("    }\n")
            append('\n')
            append("    /// Custom symbols load with `UIImage(named:)` and `Image(_:)`, not with `systemName:`.\n")
            append("    var isCustom: Bool {\n")
            append("        systemName.hasPrefix(\"$CUSTOM_PREFIX\")\n")
            append("    }\n")
            append("}\n")
        }
    }

    /** `account_circle_filled` -> `accountCircleFilled`; a Swift keyword gets backticks. */
    fun caseName(id: String): String {
        val parts = id.split('_').filter { it.isNotEmpty() }
        val name = parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        return if (name in KEYWORDS) "`$name`" else name
    }

    private data class Symbol(val case: String, val id: String, val name: String)

    private const val ID = "id"
    private const val KIND = "kind"
    private const val SF_SYMBOL = "sf_symbol"
    private const val CUSTOM_PREFIX = "custom."
    private val KINDS = setOf("shared", "custom")
    private val KEYWORDS = setOf(
        "as", "break", "case", "catch", "class", "continue", "default", "defer", "do", "else", "enum", "extension",
        "fallthrough", "false", "for", "func", "guard", "if", "import", "in", "init", "inout", "internal", "is", "let",
        "nil", "operator", "private", "protocol", "public", "repeat", "return", "self", "static", "struct", "subscript",
        "super", "switch", "throw", "throws", "true", "try", "typealias", "var", "where", "while",
    )
}
