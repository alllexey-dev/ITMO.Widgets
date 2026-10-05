package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** Koin beside Hilt (ADR 0019): where Koin modules, the global Koin context and Hilt entry points may appear. */
class DiRulesTest {

    @Test
    fun `koin modules are declared only in core di, shared feature di and di bridge`() {
        productionFiles
            .filter { file -> file.imports.any { it.name in KOIN_MODULE_BUILDERS } }
            .requireNonEmpty("files that declare a Koin module")
            .assertTrue { file ->
                val packageName = file.packagee?.name.orEmpty()
                if (file.isShared) {
                    packageName.isIn(CORE_DI_PACKAGE) || FEATURE_DI.matches(packageName)
                } else {
                    file.isAppMainOrDebug && packageName.isIn(DI_BRIDGE_PACKAGE)
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
    fun `the global koin context is read only in di bridge`() {
        productionFiles
            .filter { file -> file.imports.any { it.name in GLOBAL_KOIN } }
            .requireNonEmpty("files that read the global Koin context")
            .assertTrue { file -> file.isAppMainOrDebug && file.packagee?.name.orEmpty().isIn(DI_BRIDGE_PACKAGE) }
    }

    @Test
    fun `EntryPointAccessors is used only in di bridge`() {
        // The workers and widget entry points that read Hilt today move to KoinComponent in L17 KM-12a.
        val violations = productionFiles
            .filterNot { file -> file.isAppMainOrDebug && file.packagee?.name.orEmpty().isIn(DI_BRIDGE_PACKAGE) }
            .associate { file -> file.ratchetKey to file.imports.map { it.name }.filter { it in ENTRY_POINT_READERS } }
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, imports) -> "imports ${imports.joinToString()}" }
        Ratchet.assertOnly(RatchetRule.ENTRY_POINT_ACCESSORS, violations)
    }

    @Test
    fun `shared code carries no dagger or javax inject`() {
        // No floor: shared modules hold no sources until KM-06. The one allowed form is SP-11's bridge, an
        // androidMain `actual typealias Inject = javax.inject.Inject` that keeps moved classes Hilt-constructed.
        productionFiles
            .filter { it.isShared }
            .assertFalse { file ->
                file.text.lines().any { line ->
                    val mentionsInjection = INJECTION_PACKAGES.any { it in line }
                    mentionsInjection && !(file.isAndroidMain && INJECT_TYPEALIAS.matches(line.trim()))
                }
            }
    }

    /** The primary type of every unqualified definition in the file, in order. */
    private val KoFileDeclaration.definedTypes: List<String>
        get() = text.lines()
            .filterNot { QUALIFIER.containsMatchIn(it) }
            .flatMap { line -> DEFINITION.findAll(line).map { it.groupValues[1].ifEmpty { it.groupValues[2] } } }

    private val KoFileDeclaration.isShared: Boolean get() = projectPath.startsWith("/shared/")

    private val KoFileDeclaration.isAndroidMain: Boolean get() = "/src/androidMain/" in projectPath

    private val KoFileDeclaration.isAppMainOrDebug: Boolean
        get() = projectPath.startsWith("/app/src/main/") || projectPath.startsWith("/app/src/debug/")

    private fun String.isIn(packageName: String): Boolean = this == packageName || startsWith("$packageName.")

    private companion object {
        const val CORE_DI_PACKAGE = "dev.alllexey.itmowidgets.core.di"
        const val DI_BRIDGE_PACKAGE = "dev.alllexey.itmowidgets.di.bridge"
        val FEATURE_DI = Regex("""dev\.alllexey\.itmowidgets\.feature\.\w+\.di(\..+)?""")

        val KOIN_MODULE_BUILDERS = setOf("org.koin.dsl.module", "org.koin.dsl.lazyModule")
        val GLOBAL_KOIN = setOf("org.koin.core.context.GlobalContext", "org.koin.mp.KoinPlatform")
        val ENTRY_POINT_READERS = setOf("dagger.hilt.android.EntryPointAccessors", "dagger.hilt.EntryPoints")

        val DEFINITION = Regex("""\b(?:single|factory|scoped|viewModel|worker)(?:<([\w.]+)|Of\(::([\w.]+))""")
        val QUALIFIER = Regex("""\bnamed(?:<|\()|\bqualifier\b""")

        val INJECTION_PACKAGES = listOf("dagger.", "javax.inject")
        val INJECT_TYPEALIAS = Regex("""actual typealias \w+ = javax\.inject\.\w+""")
    }
}
