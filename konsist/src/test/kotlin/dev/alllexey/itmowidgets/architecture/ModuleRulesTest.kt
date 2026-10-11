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
    fun `no hilt binding outside di bridge`() {
        // Koin constructs everything since KM-12b; the Koin -> Hilt forwards of di/bridge for the debug test entry
        // points are the only Hilt bindings left. L17 deletes this rule with Hilt (KM-12c).
        productionFiles
            .filterNot { it.projectPath.startsWith(KOIN_TO_HILT_BRIDGE) }
            .flatMap { file -> file.hiltBindings() }
            .let { assertEquals("Hilt bindings outside di/bridge", emptyList<String>(), it) }
    }

    /** `<file>:<function>` of every `@Provides` and `@Binds` function. */
    private fun KoFileDeclaration.hiltBindings(): List<String> =
        functions(includeNested = true, includeLocal = false)
            .filter { it.hasAnnotationWithName(PROVIDES, BINDS) }
            .map { function -> "$projectPath:${function.name}" }

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
        const val KOIN_TO_HILT_BRIDGE = "/app/src/main/java/dev/alllexey/itmowidgets/di/bridge/"
        const val PROVIDES = "Provides"
        const val BINDS = "Binds"

        /** `project(":shared:feature-x")` or the type-safe `projects.shared.featureX`. */
        val FEATURE_DEPENDENCY = Regex("""project\(\s*":shared:(feature-[\w-]+)"\s*\)|projects\.shared\.(feature\w+)""")

        /** `src/<set>/kotlin/` or `src/<set>/java/` of any module; the rest of the path is the package. */
        val SOURCE_ROOT = Regex("""^.*/src/[^/]+/(kotlin|java)/?""")
    }
}
