package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** Koin is the one graph (ADR 0019): where Koin modules and the global Koin context may appear. */
class DiRulesTest {

    @Test
    fun `koin modules are declared only in core di, shared feature di and di bridge`() {
        // The app's `di` holds its Android bindings, the flavor's `updateActionModule` included; the debug source
        // set's fixtures live in `di.bridge`.
        productionFiles
            .filter { file -> file.imports.any { it.name in KOIN_MODULE_BUILDERS } }
            .requireNonEmpty("files that declare a Koin module")
            .assertTrue { file ->
                val packageName = file.packagee?.name.orEmpty()
                when {
                    file.isShared -> packageName.isIn(CORE_DI_PACKAGE) || FEATURE_DI.matches(packageName)
                    file.isAppMainOrFlavor -> packageName.isIn(DI_PACKAGE)
                    else -> file.isAppMainOrDebug && packageName.isIn(DI_BRIDGE_PACKAGE)
                }
            }
    }

    @Test
    fun `a koin module file defines each type once`() {
        // Koin keeps the last of two unqualified definitions of one type in one module without an error, and neither
        // verify() nor allowOverride(false) sees it. Put a qualifier on the definition's own line.
        productionFiles
            .filter { file -> file.imports.any { it.name in KOIN_MODULE_BUILDERS } }
            .requireNonEmpty("files that declare a Koin module")
            .also { files -> files.flatMap { it.definedTypes }.requireNonEmpty("Koin definitions") }
            .assertTrue { file -> file.definedTypes.let { it.size == it.toSet().size } }
    }

    @Test
    fun `the global koin context is read only by the app's KoinStarter`() {
        productionFiles
            .filter { file -> file.imports.any { it.name in GLOBAL_KOIN } }
            .requireNonEmpty("files that read the global Koin context")
            .assertTrue { file -> file.isAppMainOrFlavor && file.packagee?.name == DI_PACKAGE }
    }

    /** The primary type of every unqualified definition in the file, in order. */
    private val KoFileDeclaration.definedTypes: List<String>
        get() = text.lines()
            .filterNot { QUALIFIER.containsMatchIn(it) }
            .flatMap { line -> DEFINITION.findAll(line).map { it.groupValues[1].ifEmpty { it.groupValues[2] } } }

    private val KoFileDeclaration.isShared: Boolean get() = projectPath.startsWith("/shared/")

    private val KoFileDeclaration.isAppMainOrFlavor: Boolean
        get() = APP_MAIN_AND_FLAVORS.any { projectPath.startsWith(it) }

    private val KoFileDeclaration.isAppMainOrDebug: Boolean
        get() = projectPath.startsWith("/app/src/main/") || projectPath.startsWith("/app/src/debug/")

    private fun String.isIn(packageName: String): Boolean = this == packageName || startsWith("$packageName.")

    private companion object {
        const val CORE_DI_PACKAGE = "dev.alllexey.itmowidgets.core.di"
        const val DI_BRIDGE_PACKAGE = "dev.alllexey.itmowidgets.di.bridge"
        val APP_MAIN_AND_FLAVORS = listOf("/app/src/main/", "/app/src/github/", "/app/src/play/")
        val FEATURE_DI = Regex("""dev\.alllexey\.itmowidgets\.feature\.\w+\.di(\..+)?""")

        val KOIN_MODULE_BUILDERS = setOf("org.koin.dsl.module", "org.koin.dsl.lazyModule")
        val GLOBAL_KOIN = setOf("org.koin.core.context.GlobalContext", "org.koin.mp.KoinPlatform")

        val DEFINITION = Regex("""\b(?:single|factory|scoped|viewModel|worker)(?:<([\w.]+)|Of\(::([\w.]+))""")
        val QUALIFIER = Regex("""\bnamed(?:<|\()|\bqualifier\b""")
    }
}
