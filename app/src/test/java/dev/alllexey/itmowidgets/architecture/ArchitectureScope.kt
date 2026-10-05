package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.io.File
import org.junit.Assert.assertTrue

/** The production sources every `*RulesTest` checks, parsed once per test JVM. */
internal object ArchitectureScope {

    /**
     * Every module's production source sets, so the shared modules join once they exist. Agent worktrees under
     * .claude/ are separate checkouts, not this app's sources. The project path, not the absolute one: inside such a
     * worktree the absolute path of every file contains /.claude/. Build logic and the Konsist module are tooling.
     */
    val productionFiles: List<KoFileDeclaration> by lazy {
        Konsist.scopeFromProduction().files.filterNot { file ->
            "/.claude/" in file.projectPath || EXCLUDED_MODULES.any { file.projectPath.startsWith("/$it/") }
        }
    }

    val productionClasses: List<KoClassDeclaration> by lazy { productionFiles.flatMap { it.classes() } }

    /** A file of [module], resolved from the project root rather than the working directory of the test task. */
    fun moduleFile(module: String, path: String): File = File(File(Konsist.projectRootPath, module), path)

    private val EXCLUDED_MODULES = listOf("build-logic", "konsist")
}

/** A rule over an empty set proves nothing, so each rule checks the size of its scope before asserting on it. */
internal fun <T> List<T>.requireAtLeast(floor: Int, what: String): List<T> = also {
    assertTrue("Only $size $what in scope, expected at least $floor", size >= floor)
}

internal fun <T> List<T>.requireNonEmpty(what: String): List<T> = requireAtLeast(1, what)

internal const val APP_MODULE = "app"
internal const val FEATURE_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.feature."
internal const val CORE_PACKAGE_PREFIX = "dev.alllexey.itmowidgets.core."
internal const val CORE_STORAGE_PACKAGE = "dev.alllexey.itmowidgets.core.storage"

/** Floors for sets that must not shrink; they drop only with the integrator's OK. */
internal const val MIN_GATED_CLASSES = 20
internal const val MIN_VIEW_MODELS = 34
internal const val MIN_DOMAIN_FILES = 95
internal const val MIN_DEBUG_ONLY_CLASSES = 5
