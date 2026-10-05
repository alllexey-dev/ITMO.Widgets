package dev.alllexey.itmowidgets.buildlogic.strings

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** One `<string>` or `<plurals>` entry; [texts] holds the string value or every plural item. */
data class CatalogEntry(
    val key: String,
    val kind: String,
    val texts: List<String>,
    val quantities: Set<String>,
    val attributes: Set<String>,
)

/** A catalog file in Android resource syntax; [path] is the repository-relative path used in messages. */
class CatalogFile(val path: String, val entries: List<CatalogEntry>) {
    val fileName: String get() = path.substringAfterLast('/')

    companion object {
        fun parse(path: String, file: File): CatalogFile {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val entries = children(root).filter { it.tagName == STRING || it.tagName == PLURALS }.map { element ->
                val items = if (element.tagName == PLURALS) children(element).filter { it.tagName == "item" } else emptyList()
                CatalogEntry(
                    key = element.getAttribute("name"),
                    kind = element.tagName,
                    texts = if (element.tagName == PLURALS) items.map { it.textContent } else listOf(element.textContent),
                    quantities = items.map { it.getAttribute("quantity") }.toSet(),
                    attributes = (listOf(element) + items).flatMap { attributeNames(it) }.toSet(),
                )
            }
            return CatalogFile(path, entries)
        }

        private fun children(element: Element): List<Element> =
            (0 until element.childNodes.length).map { element.childNodes.item(it) }.filterIsInstance<Element>()

        private fun attributeNames(element: Element): List<String> =
            (0 until element.attributes.length).map { element.attributes.item(it).nodeName }
    }
}

/**
 * The `checkStringCatalog` rules (ADR 0028, report 95 "Tooling"). Each rule returns one message per violation;
 * an empty list means the catalog passes. The freshness of generated outputs is TC-16b's rule.
 */
object StringCatalogRules {

    /** Placeholders are `%N$s`/`%N$d`: CMP, Android and the Apple export agree only on positional arguments. */
    fun positionalPlaceholders(files: List<CatalogFile>): List<String> = files.flatMap { file ->
        file.entries.flatMap { entry ->
            entry.texts.flatMap { text -> nonPositional(text) }.distinct()
                .map { "${file.path}: ${entry.key} has the placeholder '$it'; use %1\$s, %1\$d or %%" }
        }
    }

    /** Russian needs `one`, `few`, `many` and `other`; a missing form falls back to `other` silently. */
    fun completePlurals(files: List<CatalogFile>): List<String> = files.flatMap { file ->
        file.entries.filter { it.kind == PLURALS }.mapNotNull { entry ->
            val missing = RUSSIAN_QUANTITIES - entry.quantities
            if (missing.isEmpty()) null else "${file.path}: ${entry.key} lacks the plural forms ${missing.sorted()}"
        }
    }

    /** CMP allows a key once per module only; Android `R` and the flat Apple tables need it once overall. */
    fun uniqueKeys(files: List<CatalogFile>): List<String> =
        files.flatMap { file -> file.entries.map { it.key to file.path } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .toSortedMap()
            .map { (key, paths) -> "$key is defined ${paths.size} times: ${paths.joinToString()}" }

    /** The catalog has one language; `translatable` would only mark URLs and configuration, which stay in code. */
    fun noTranslatable(files: List<CatalogFile>): List<String> = files.flatMap { file ->
        file.entries.filter { TRANSLATABLE in it.attributes }.map { "${file.path}: ${it.key} has translatable" }
    }

    /**
     * Frozen keys reach system surfaces and APNs `loc-key`s of installed versions: the list is sorted, unique and
     * every key stays in a `strings_platform.xml`.
     */
    fun frozenKeys(files: List<CatalogFile>, frozen: List<String>): List<String> {
        val order = if (frozen != frozen.distinct().sorted()) listOf("$FROZEN_KEYS is not sorted and unique") else emptyList()
        val platformKeys = files.filter { it.fileName == PLATFORM_FILE }.flatMap { file -> file.entries.map { it.key } }.toSet()
        return order + frozen.distinct().filter { it !in platformKeys }
            .map { "frozen key $it is missing from every $PLATFORM_FILE" }
    }

    /** URLs are configuration, not copy; the bare `https://` of a hint carries no host and passes. */
    fun noUrls(files: List<CatalogFile>): List<String> = files.flatMap { file ->
        file.entries.filter { entry -> entry.texts.any { URL.containsMatchIn(it) } }
            .map { "${file.path}: ${it.key} holds a URL; move it to a constant" }
    }

    fun check(files: List<CatalogFile>, frozen: List<String>): List<String> =
        positionalPlaceholders(files) + completePlurals(files) + uniqueKeys(files) + noTranslatable(files) +
            frozenKeys(files, frozen) + noUrls(files)

    private fun nonPositional(text: String): List<String> {
        val found = mutableListOf<String>()
        var index = text.indexOf('%')
        while (index >= 0) {
            if (text.startsWith("%%", index)) {
                index = text.indexOf('%', index + 2)
                continue
            }
            val positional = POSITIONAL.matchAt(text, index)
            if (positional == null) found += text.substring(index, minOf(text.length, index + 3)).trimEnd()
            index = text.indexOf('%', positional?.range?.last?.plus(1) ?: (index + 1))
        }
        return found
    }

    const val PLATFORM_FILE = "strings_platform.xml"
    const val FROZEN_KEYS = "scripts/strings-frozen-keys.txt"
    private const val TRANSLATABLE = "translatable"
    private val RUSSIAN_QUANTITIES = setOf("one", "few", "many", "other")
    private val POSITIONAL = Regex("""%[1-9]\d*\$[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")
    private val URL = Regex("""\b[A-Za-z][A-Za-z0-9+.-]*://[^\s/<"']""")
}

private const val STRING = "string"
private const val PLURALS = "plurals"
