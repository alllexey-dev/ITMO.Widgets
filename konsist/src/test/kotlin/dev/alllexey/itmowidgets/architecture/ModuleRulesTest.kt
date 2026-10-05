package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.testFiles
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Module boundaries, moves that keep packages (G-03) and one DI graph per binding (ADR 0019, master plan X7). */
class ModuleRulesTest {

    @Test
    fun `a shared feature module depends on no other shared feature module`() {
        val violations = sharedFeatureBuildFiles()
            .requireAtLeast(MIN_SHARED_FEATURE_MODULES, "shared feature build files")
            .flatMap { buildFile ->
                val module = buildFile.parentFile.name
                FEATURE_DEPENDENCY.findAll(buildFile.readText())
                    .map { match -> match.groupValues[1].ifEmpty { match.groupValues[2] } }
                    .map { "shared/$module/build.gradle.kts depends on $it" }
                    .toList()
            }
        assertEquals("Features never depend on each other", emptyList<String>(), violations)
    }

    @Test
    fun `a file lives in the directory of its package`() {
        // A move to a shared module is a `git mv` under the same package, so imports and stable FQCNs never change.
        (productionFiles + testFiles)
            .filter { file -> SOURCE_ROOT.containsMatchIn(file.projectPath) }
            .requireAtLeast(MIN_DOMAIN_FILES, "Kotlin files under a source root")
            .assertTrue { file ->
                val directory = file.projectPath.substringBeforeLast('/').replace(SOURCE_ROOT, "")
                directory == file.packagee?.name.orEmpty().replace('.', '/')
            }
    }

    @Test
    fun `a type constructed by koin has no hilt binding outside di bridge`() {
        // The Koin -> Hilt forwards of di/bridge are the only second door; L17 deletes this rule with Hilt (KM-12c).
        val koinTypes = koinConstructedTypes().toList().requireNonEmpty("types Koin constructs").toSet()
        productionFiles
            .filterNot { it.projectPath.startsWith(KOIN_TO_HILT_BRIDGE) }
            .flatMap { file -> file.hiltBindings() }
            .requireNonEmpty("Hilt @Provides and @Binds functions")
            .filter { (_, types) -> types.any(koinTypes::contains) }
            .map { (binding, _) -> binding }
            .let { assertEquals("Koin-constructed types bound by Hilt too", emptyList<String>(), it) }
    }

    /** Primary and `bind<T>()` types of every Koin module outside the bridges, by fully qualified name. */
    private fun koinConstructedTypes(): Set<String> = productionFiles
        .filter { file -> file.imports.any { it.name in KOIN_MODULE_BUILDERS } }
        .filterNot { file -> file.packagee?.name.orEmpty().let { it == DI_BRIDGE || it.startsWith("$DI_BRIDGE.") } }
        .flatMap { file ->
            KOIN_DEFINITION.findAll(file.text)
                .map { match -> match.groupValues.drop(1).first(String::isNotEmpty) }
                .flatMap { file.candidateNames(it) }
                .toList()
        }
        .toSet()

    /**
     * `<file>:<function>` to the fully qualified names it binds: the return type, or for a multibinding contribution
     * (`@IntoSet`, `@IntoMap`) the implementation a `@Binds` takes. A contribution returns the element type of an open
     * set both graphs feed (`SessionDataCleaner`: Hilt's `@IntoSet` plus Koin's qualified singles, merged by
     * `SessionCleanersBridge`), so the element type is not a binding of its own; binding a Koin-built implementation
     * into a Hilt set still fails. A `@Provides` contribution builds its element in the body and is not checked.
     */
    private fun KoFileDeclaration.hiltBindings(): List<Pair<String, Set<String>>> =
        functions(includeNested = true, includeLocal = false)
            .filter { it.hasAnnotationWithName(PROVIDES, BINDS) }
            .mapNotNull { function ->
                val binding = "$projectPath:${function.name}"
                if (function.hasAnnotationWithName(INTO_SET, INTO_MAP)) {
                    val bound = if (function.hasAnnotationWithName(BINDS)) function.parameters else emptyList()
                    return@mapNotNull binding to bound.flatMap { candidateNames(it.type.name) }.toSet()
                }
                val returnType = function.returnType?.name ?: return@mapNotNull null
                binding to candidateNames(returnType)
            }

    private fun sharedFeatureBuildFiles(): List<File> =
        File(Konsist.projectRootPath, SHARED).listFiles { file -> file.name.startsWith(FEATURE_MODULE_PREFIX) }
            .orEmpty()
            .map { File(it, BUILD_FILE) }
            .filter(File::isFile)
            .sortedBy { it.path }

    private companion object {
        const val SHARED = "shared"
        const val FEATURE_MODULE_PREFIX = "feature-"
        const val BUILD_FILE = "build.gradle.kts"
        const val DI_BRIDGE = "dev.alllexey.itmowidgets.di.bridge"
        const val KOIN_TO_HILT_BRIDGE = "/app/src/main/java/dev/alllexey/itmowidgets/di/bridge/"
        const val PROVIDES = "Provides"
        const val BINDS = "Binds"
        const val INTO_SET = "IntoSet"
        const val INTO_MAP = "IntoMap"

        /** `project(":shared:feature-x")` or the type-safe `projects.shared.featureX`. */
        val FEATURE_DEPENDENCY = Regex("""project\(\s*":shared:(feature-[\w-]+)"\s*\)|projects\.shared\.(feature\w+)""")

        /** `src/<set>/kotlin/` or `src/<set>/java/` of any module; the rest of the path is the package. */
        val SOURCE_ROOT = Regex("""^.*/src/[^/]+/(kotlin|java)/?""")

        val KOIN_MODULE_BUILDERS = setOf("org.koin.dsl.module", "org.koin.dsl.lazyModule")
        val KOIN_DEFINITION = Regex(
            """\b(?:single|factory|scoped|viewModel|worker)(?:<([\w.]+)|Of\(::([\w.]+))|\bbind<([\w.]+)>"""
        )
    }
}
