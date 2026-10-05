package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/** The production sources every `*RulesTest` checks, parsed once per test JVM. */
internal object ArchitectureScope {

    /**
     * Every module's production source sets, so the shared modules join once they exist. Agent worktrees under
     * .claude/ are separate checkouts, not this app's sources. The project path, not the absolute one: inside such a
     * worktree the absolute path of every file contains /.claude/. Build logic and the Konsist module are tooling.
     */
    val productionFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromProduction().files.filterNot(::isTooling) }

    /** Every module's test source sets (unit and instrumented), filtered like [productionFiles]. */
    val testFiles: List<KoFileDeclaration> by lazy { Konsist.scopeFromTest().files.filterNot(::isTooling) }

    val productionClasses: List<KoClassDeclaration> by lazy { productionFiles.flatMap { it.classes() } }

    /** A file of [module], resolved from the project root rather than the working directory of the test task. */
    fun moduleFile(module: String, path: String): File = File(File(Konsist.projectRootPath, module), path)

    private fun isTooling(file: KoFileDeclaration): Boolean =
        "/.claude/" in file.projectPath || EXCLUDED_MODULES.any { file.projectPath.startsWith("/$it/") }

    private val EXCLUDED_MODULES = listOf("build-logic", KONSIST_MODULE)
}

/** A rule over an empty set proves nothing, so each rule checks the size of its scope before asserting on it. */
internal fun <T> List<T>.requireAtLeast(floor: Int, what: String): List<T> = also {
    assertTrue("Only $size $what in scope, expected at least $floor", size >= floor)
}

internal fun <T> List<T>.requireNonEmpty(what: String): List<T> = requireAtLeast(1, what)

internal const val APP_MODULE = "app"

/** This module: it holds the rules and their ratchet files, and its own sources are never in scope. */
internal const val KONSIST_MODULE = "konsist"

internal const val ROOT_PACKAGE = "dev.alllexey.itmowidgets"
internal const val FEATURE_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.feature."
internal const val CORE_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.core."
internal const val APP_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.app."
internal const val DI_PACKAGE = "dev.alllexey.itmowidgets.di"
internal const val CORE_STORAGE_PACKAGE = "dev.alllexey.itmowidgets.core.storage"

/** Floors for sets that must not shrink; they drop only with the integrator's OK. */
internal const val MIN_GATED_CLASSES = 20
internal const val MIN_VIEW_MODELS = 34
internal const val MIN_DOMAIN_FILES = 95
internal const val MIN_DEBUG_ONLY_CLASSES = 5
internal const val MIN_BACKEND_GATED_CLASSES = 16

/** The feature a package or import belongs to, or null outside `feature.*`. */
internal fun featureOf(name: String): String? = name
    .takeIf { it.startsWith(FEATURE_PACKAGE_PREFIX) }
    ?.removePrefix(FEATURE_PACKAGE_PREFIX)
    ?.substringBefore(".")

/** The key a file-level rule reports and a ratchet line names: the package plus the file name without `.kt`. */
internal val KoFileDeclaration.ratchetKey: String
    get() = listOfNotNull(packagee?.name, name).joinToString(".")

/** The key a class-level rule reports and a ratchet line names: the package plus the class name. */
internal val KoClassDeclaration.ratchetKey: String
    get() = listOfNotNull(packagee?.name, name).joinToString(".")

/**
 * Rules that start with known violations. Their allowlists are the files of [Ratchet.DIRECTORY], one per owner, so a
 * lane that fixes a violation deletes the line from its own file only.
 */
internal enum class RatchetRule(val id: String) {
    VIEW_MODEL_SHAPE("viewmodel-shape"),
    PRESENTATION_RESOURCES("presentation-imports-r"),
    DOMAIN_JAVAX_INJECT("domain-imports-javax-inject"),
    DOMAIN_JAVA("domain-imports-java"),
    ENTRY_POINT_ACCESSORS("entry-point-accessors");

    companion object {
        fun byId(id: String): RatchetRule? = entries.firstOrNull { it.id == id }
    }
}

/** One allowlist line: `<rule-id> <key>` in [file] at [line]. */
internal data class RatchetEntry(val file: String, val line: Int, val ruleId: String, val key: String)

/**
 * The allowlists of the [RatchetRule]s, read from every file of [DIRECTORY]; there is no registry of files.
 *
 * A file is `<feature>.txt` for `feature.<feature>.*`, `core.txt` for `core.*` and `app.txt` for everything else.
 * Lines are `<rule-id> <key>`; blank lines and lines starting with `#` are ignored. Entries only ever go away: a key
 * that no longer violates its rule fails as stale, and a new violation is fixed, never added.
 */
internal object Ratchet {

    const val DIRECTORY = "src/test/resources/architecture/ratchet"

    val directory: File get() = ArchitectureScope.moduleFile(KONSIST_MODULE, DIRECTORY)

    val entries: List<RatchetEntry> by lazy {
        directory.listFiles { file -> file.isFile }.orEmpty().sortedBy { it.name }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
                val parts = line.split(Regex("\\s+"))
                RatchetEntry(file.name, index + 1, parts[0], parts.drop(1).joinToString(" "))
            }
        }
    }

    /** The allowlist file that owns [key]. */
    fun fileFor(key: String): String = when {
        key.startsWith(FEATURE_PACKAGE_PREFIX) -> "${featureOf(key)}.txt"
        key.startsWith(CORE_PACKAGE_PREFIX) -> "core.txt"
        else -> "app.txt"
    }

    /**
     * Fails when [violations] (key to reason) holds a key that [rule]'s allowlist lacks, or the allowlist holds a key
     * that no longer violates. The message lists ready-to-delete lines and never suggests adding one.
     */
    fun assertOnly(rule: RatchetRule, violations: Map<String, String>) {
        val allowed = entries.filter { it.ruleId == rule.id }
        val allowedKeys = allowed.map { it.key }.toSet()
        val added = violations.filterKeys { it !in allowedKeys }.toSortedMap()
        val stale = allowed.filter { it.key !in violations }
        if (added.isEmpty() && stale.isEmpty()) return
        val message = buildString {
            if (added.isNotEmpty()) {
                appendLine("${added.size} new violation(s) of ${rule.id}; fix them, the ratchet never grows:")
                added.forEach { (key, reason) -> appendLine("  $key: $reason") }
            }
            if (stale.isNotEmpty()) {
                appendLine("${stale.size} stale line(s) of ${rule.id}; the code is fixed, delete them:")
                stale.forEach {
                    appendLine("  $KONSIST_MODULE/$DIRECTORY/${it.file}:${it.line} ${it.ruleId} ${it.key}")
                }
            }
        }
        fail(message)
    }
}
