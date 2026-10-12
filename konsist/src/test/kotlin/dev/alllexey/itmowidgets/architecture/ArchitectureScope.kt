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
     * Every module's production source sets, so new modules join automatically. Git worktrees under
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

/** This module: it holds the rules, and its own sources are never in scope. */
internal const val KONSIST_MODULE = "konsist"

internal const val FEATURE_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.feature."
internal const val CORE_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.core."
internal const val APP_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.app."
internal const val DI_PACKAGE = "dev.alllexey.itmowidgets.di"

/** Floors for sets that must not shrink; lower one only when the code it counts is deliberately removed. */
internal const val MIN_GATED_CLASSES = 20
internal const val MIN_VIEW_MODELS = 34
internal const val MIN_DOMAIN_FILES = 95

/** The two sport debug stores and the time override store. */
internal const val MIN_DEBUG_ONLY_CLASSES = 3
internal const val MIN_BACKEND_GATED_CLASSES = 16
internal const val MIN_KMP_SOURCE_FILES = 250
internal const val MIN_SHARED_FEATURE_MODULES = 10

/** The feature a package or import belongs to, or null outside `feature.*`. */
internal fun featureOf(name: String): String? = name
    .takeIf { it.startsWith(FEATURE_PACKAGE_PREFIX) }
    ?.removePrefix(FEATURE_PACKAGE_PREFIX)
    ?.substringBefore(".")

/** The key a file-level rule reports: the package plus the file name without `.kt`. */
internal val KoFileDeclaration.qualifiedKey: String
    get() = listOfNotNull(packagee?.name, name).joinToString(".")

/** The key a class-level rule reports: the package plus the class name. */
internal val KoClassDeclaration.qualifiedKey: String
    get() = listOfNotNull(packagee?.name, name).joinToString(".")

/** Fails with every key and its reason when [violations] (key to reason) is not empty. */
internal fun assertNoViolations(rule: String, violations: Map<String, String>) {
    if (violations.isEmpty()) return
    fail(violations.toSortedMap().entries.joinToString("\n", "${violations.size} violation(s) of $rule:\n") {
        "  ${it.key}: ${it.value}"
    })
}
