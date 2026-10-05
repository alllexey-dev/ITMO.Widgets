package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration

/**
 * The fully qualified names a type reference in [file] may stand for, read from the file's imports and package the way
 * the compiler does: an explicit or aliased import wins, a qualified reference is taken as written, and otherwise the
 * file's own package and every wildcard import are candidates. Two classes with one simple name (the app's
 * `feature.recordbook.data.bars.BarsClient` and MyItmoApi's `dev.alllexey.itmoapi.bars.BarsClient`) never mix.
 */
internal fun KoFileDeclaration.candidateNames(typeReference: String): Set<String> {
    val reference = typeReference.substringBefore('<').removeSuffix("?").trim()
    if (reference.isEmpty()) return emptySet()
    val head = reference.substringBefore('.')
    val tail = reference.removePrefix(head)
    if (head.first().isLowerCase()) return setOf(reference)
    val imports = importsByText()
    imports.explicit[head]?.let { return setOf(it + tail) }
    val packageName = packagee?.name
    val local = if (packageName.isNullOrEmpty()) reference else "$packageName.$reference"
    return setOf(local) + imports.wildcards.map { "$it.$reference" }
}

private class FileImports(val explicit: Map<String, String>, val wildcards: List<String>)

private fun KoFileDeclaration.importsByText(): FileImports {
    val explicit = mutableMapOf<String, String>()
    val wildcards = mutableListOf<String>()
    imports.forEach { import ->
        val match = IMPORT.find(import.text.trim()) ?: return@forEach
        val (path, star, alias) = match.destructured
        val name = path.replace("`", "")
        when {
            star.isNotEmpty() -> wildcards += name
            alias.isNotEmpty() -> explicit[alias] = name
            else -> explicit[name.substringAfterLast('.')] = name
        }
    }
    return FileImports(explicit, wildcards)
}

private val IMPORT = Regex("""^import\s+([\w.`]+?)(\.\*)?(?:\s+as\s+(\w+))?\s*;?$""")
