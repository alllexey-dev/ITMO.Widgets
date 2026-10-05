package dev.alllexey.itmowidgets.buildlogic.strings

import org.gradle.api.GradleException
import java.io.File

/**
 * The committed Apple files of the catalog (ADR 0028): `Strings/<table>.xcstrings` and `Symbols/AppSymbol.swift`
 * under `iosApp/Shared`, produced by `:app:exportAppleStrings` and checked by `checkStringCatalog`.
 *
 * - One table per catalog file name (`strings_settings.xml` -> `strings_settings.xcstrings`), so a `git mv` between
 *   modules changes no table and same-named files feed one table.
 * - `strings_platform.xml` and the Apple-only `strings_ios*.xml` go to `Localizable`: APNs resolves a `loc-key`
 *   only there.
 * - `InfoPlist` and `AppShortcuts` rows come from `apple-tables.properties`; `strings_debug.xml` is left out.
 *
 * The Swift side reads a key from `Localizable` first, then from the file tables. A plural takes its count as
 * argument 1 (`%1$lld`, or the `count` substitution when no form shows it); `%lld` takes `Int64`, `%@` a `String`.
 */
object AppleExport {

    const val STRINGS_DIR = "Strings"
    const val SYMBOLS_FILE = "Symbols/AppSymbol.swift"
    const val COMMAND = "scripts/verify.sh run -- :app:exportAppleStrings"
    const val TABLES_FILE = "build-logic/strings/apple-tables.properties"
    const val ICONS_FILE = "docs/design/icons.tsv"

    /** Every committed file, keyed by its path relative to `iosApp/Shared`. */
    fun render(catalog: List<CatalogFile>, tableRows: List<String>, iconRows: List<String>): Map<String, String> =
        XcStrings.tables(catalog, AppleTableRow.parse(tableRows))
            .mapKeys { (table, _) -> "$STRINGS_DIR/$table.xcstrings" } +
            (SYMBOLS_FILE to AppSymbolSwift.render(iconRows))

    /** Writes [outputs] into [sharedDir] and deletes every table the export no longer produces. */
    fun write(outputs: Map<String, String>, sharedDir: File) {
        committedTables(sharedDir).filter { it !in outputs }.forEach { File(sharedDir, it).delete() }
        outputs.forEach { (path, text) ->
            val file = File(sharedDir, path)
            file.parentFile.mkdirs()
            if (!file.isFile || file.readText() != text) file.writeText(text)
        }
    }

    /** One message per committed file that differs from [outputs]; [prefix] is `sharedDir` in messages. */
    fun staleOutputs(outputs: Map<String, String>, sharedDir: File, prefix: String): List<String> {
        val extra = committedTables(sharedDir).filter { it !in outputs }
            .map { "$prefix/$it is not produced by the export" }
        val changed = outputs.toSortedMap().mapNotNull { (path, text) ->
            val file = File(sharedDir, path)
            when {
                !file.isFile -> "$prefix/$path is missing"
                file.readText() != text -> "$prefix/$path differs from a fresh export"
                else -> null
            }
        }
        return changed + extra
    }

    private fun committedTables(sharedDir: File): List<String> =
        File(sharedDir, STRINGS_DIR).listFiles { file -> file.isFile && file.name.endsWith(".xcstrings") }.orEmpty()
            .map { "$STRINGS_DIR/${it.name}" }.sorted()
}

/** `<Table>.<key>=<catalog key>`: the `.xcstrings` key [key] of [table] shows the text of [source]. */
data class AppleTableRow(val table: String, val key: String, val source: String) {

    companion object {
        val TABLES = listOf("InfoPlist", "AppShortcuts")

        fun parse(lines: List<String>): List<AppleTableRow> {
            val rows = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
                val name = line.substringBefore('=', "").trim()
                val source = line.substringAfter('=', "").trim()
                val table = name.substringBefore('.', "")
                val key = name.substringAfter('.', "")
                if (table !in TABLES || key.isEmpty() || source.isEmpty()) {
                    throw GradleException(
                        "${AppleExport.TABLES_FILE}: '$line' is not <Table>.<key>=<catalog key> with Table in $TABLES",
                    )
                }
                AppleTableRow(table, key, source)
            }
            rows.groupBy { it.table to it.key }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { (table, key) ->
                throw GradleException("${AppleExport.TABLES_FILE}: $table.$key is defined twice")
            }
            return rows
        }
    }
}

/** Android catalog entries as Xcode string catalogs (`sourceLanguage: ru`, every key `extractionState: manual`). */
object XcStrings {

    const val LOCALIZABLE = "Localizable"

    /** The table of a catalog file, or null for a file the export leaves out. */
    fun tableOf(file: CatalogFile): String? {
        val name = file.fileName
        val directory = file.path.substringBeforeLast('/', "").substringAfterLast('/')
        return when {
            directory.startsWith("values-") -> null
            name == DEBUG_FILE -> null
            name == StringCatalogRules.PLATFORM_FILE || IOS_FILE.matches(name) -> LOCALIZABLE
            else -> name.removeSuffix(".xml")
        }
    }

    /** Table name -> `.xcstrings` text; tables without entries are not written. */
    fun tables(catalog: List<CatalogFile>, rows: List<AppleTableRow>): Map<String, String> {
        val byTable = mutableMapOf<String, MutableMap<String, Any>>()
        catalog.forEach { file ->
            val table = tableOf(file) ?: return@forEach
            file.entries.forEach { entry -> put(byTable, table, entry.key, unit(file, entry)) }
        }
        val byKey = catalog.filter { tableOf(it) != null }
            .flatMap { file -> file.entries.map { it.key to (file to it) } }.toMap()
        rows.forEach { row ->
            val name = "${AppleExport.TABLES_FILE}: ${row.table}.${row.key}"
            val (file, entry) = byKey[row.source] ?: throw GradleException("$name names no catalog key ${row.source}")
            if (entry.isPlural) throw GradleException("$name names the plural ${row.source}")
            val comment = "Generated from ${file.fileName} (${row.source})"
            put(byTable, row.table, row.key, string(comment, entry.texts.single()))
        }
        return byTable.toSortedMap().mapValues { (_, strings) ->
            XcodeJson.write(mapOf("sourceLanguage" to LANGUAGE, "strings" to strings, "version" to "1.0"))
        }
    }

    private fun put(byTable: MutableMap<String, MutableMap<String, Any>>, table: String, key: String, unit: Any) {
        val strings = byTable.getOrPut(table) { mutableMapOf() }
        if (strings.put(key, unit) != null) throw GradleException("$key appears twice in the Apple table $table")
    }

    private fun unit(file: CatalogFile, entry: CatalogEntry): Map<String, Any> {
        if (entry.markup) throw GradleException("${file.path}: ${entry.key} has markup the Apple export cannot carry")
        val comment = "Generated from ${file.fileName}"
        if (!entry.isPlural) return string(comment, entry.texts.single())
        val forms = entry.forms.mapValues { (_, text) -> appleValue(text) }
        val plural = mapOf("plural" to forms.mapValues { (_, value) -> stringUnit(value) })
        val localization = when {
            entry.forms.values.any { COUNT.containsMatchIn(it) } -> mapOf("variations" to plural)
            entry.forms.values.none { PLACEHOLDER.containsMatchIn(it) } -> mapOf(
                "stringUnit" to mapOf("state" to STATE, "value" to "%#@$COUNT_NAME@"),
                "substitutions" to mapOf(
                    COUNT_NAME to mapOf("argNum" to 1, "formatSpecifier" to "lld", "variations" to plural),
                ),
            )
            else -> throw GradleException(
                "${file.path}: ${entry.key} has arguments but no %1\$d; Apple selects a plural by argument 1",
            )
        }
        return entry(comment, localization)
    }

    private fun string(comment: String, text: String) = entry(comment, stringUnit(appleValue(text)))

    private fun stringUnit(value: String) = mapOf("stringUnit" to mapOf("state" to STATE, "value" to value))

    private fun entry(comment: String, localization: Map<String, Any>) = mapOf(
        "comment" to comment,
        "extractionState" to "manual",
        "localizations" to mapOf(LANGUAGE to localization),
    )

    /** An Android resource value as an Apple format string: escapes resolved, `%N$s` -> `%N$@`, `%N$d` -> `%N$lld`. */
    fun appleValue(android: String): String = FORMAT.replace(AndroidText.decode(android)) { match ->
        val (position, flags, conversion) = match.destructured
        when {
            position.isEmpty() -> match.value
            conversion == "s" || conversion == "S" -> "%$position\$$flags@"
            conversion == "d" -> "%$position\$${flags}lld"
            else -> match.value
        }
    }

    private const val LANGUAGE = "ru"
    private const val STATE = "translated"
    private const val COUNT_NAME = "count"
    private const val DEBUG_FILE = "strings_debug.xml"
    private val IOS_FILE = Regex("""strings_ios[A-Za-z0-9_]*\.xml""")
    private val FORMAT = Regex("""%%|%([1-9]\d*)\$([-#+ 0,(]*\d*(?:\.\d+)?)([a-zA-Z])""")
    private val PLACEHOLDER = Regex("""%[1-9]\d*\$""")
    private val COUNT = Regex("""%1\$[-#+ 0,(]*\d*d""")
}

/** aapt2's reading of a resource value: escapes, `"` quoting, collapsed and trimmed unquoted whitespace. */
object AndroidText {

    fun decode(raw: String): String {
        val chars = StringBuilder()
        // True where a character is escaped or quoted: whitespace collapsing and trimming leave it alone.
        val kept = mutableListOf<Boolean>()
        fun add(char: Char, keep: Boolean) {
            chars.append(char)
            kept += keep
        }
        var quoted = false
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                val next = raw[index + 1]
                if (next == 'u' && index + UNICODE_ESCAPE <= raw.length) {
                    add(raw.substring(index + 2, index + UNICODE_ESCAPE).toInt(HEX).toChar(), true)
                    index += UNICODE_ESCAPE
                } else {
                    add(ESCAPES[next] ?: next, true)
                    index += 2
                }
                continue
            }
            when {
                char == '"' -> quoted = !quoted
                quoted -> add(char, true)
                char.isWhitespace() -> if (chars.isEmpty() || kept.last() || chars.last() != ' ') add(' ', false)
                else -> add(char, false)
            }
            index++
        }
        var start = 0
        var end = chars.length
        while (start < end && !kept[start] && chars[start] == ' ') start++
        while (end > start && !kept[end - 1] && chars[end - 1] == ' ') end--
        return chars.substring(start, end)
    }

    private const val UNICODE_ESCAPE = 6
    private const val HEX = 16
    private val ESCAPES = mapOf('n' to '\n', 't' to '\t')
}
