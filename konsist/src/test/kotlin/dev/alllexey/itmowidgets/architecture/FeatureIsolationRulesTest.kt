package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.testFiles
import org.junit.Test

class FeatureIsolationRulesTest {

    @Test
    fun `features do not depend directly on other features`() {
        productionFiles
            .filter { it.packagee?.name?.startsWith(FEATURE_PACKAGE_PREFIX) == true }
            .requireNonEmpty("feature files")
            .assertFalse { file ->
                val sourceFeature = file.packagee
                    ?.name
                    ?.removePrefix(FEATURE_PACKAGE_PREFIX)
                    ?.substringBefore(".")
                    ?: return@assertFalse false

                file.imports.any { import ->
                    val targetFeature = import.name
                        .takeIf { it.startsWith(FEATURE_PACKAGE_PREFIX) }
                        ?.removePrefix(FEATURE_PACKAGE_PREFIX)
                        ?.substringBefore(".")
                        ?: return@any false

                    sourceFeature != targetFeature
                }
            }
    }

    @Test
    fun `core does not depend on features`() {
        productionFiles
            .filter { it.packagee?.name?.startsWith(CORE_PACKAGE_PREFIX) == true }
            .requireNonEmpty("core files")
            .assertFalse { file ->
                file.imports.any { it.name.startsWith(FEATURE_PACKAGE_PREFIX) }
            }
    }

    @Test
    fun `features and core do not depend on the app shell`() {
        productionFiles
            .filter { file ->
                val packageName = file.packagee?.name.orEmpty()
                packageName.startsWith(FEATURE_PACKAGE_PREFIX) || packageName.startsWith(CORE_PACKAGE_PREFIX)
            }
            .requireNonEmpty("feature and core files")
            .assertFalse { file ->
                file.imports.any { it.name.startsWith(APP_PACKAGE_PREFIX) }
            }
    }

    @Test
    fun `feature tests do not depend on another feature's tests`() {
        // Shared fakes live in core/testing (AA-09); a fake that two features need moves there.
        val testDeclarations = testFiles
            .requireNonEmpty("test files")
            .flatMap { file ->
                val packageName = file.packagee?.name ?: return@flatMap emptyList()
                topLevelNames(file).map { "$packageName.$it" }
            }
            .toSet()
        testFiles
            .filter { it.packagee?.name?.startsWith(FEATURE_PACKAGE_PREFIX) == true }
            .requireNonEmpty("feature test files")
            .assertFalse { file ->
                val sourceFeature = featureOf(file.packagee?.name.orEmpty())
                file.imports.any { import ->
                    val targetFeature = featureOf(import.name)
                    targetFeature != null && targetFeature != sourceFeature &&
                        import.name.declaredIn(testDeclarations)
                }
            }
    }

    private fun topLevelNames(file: KoFileDeclaration): List<String> =
        file.classes(includeNested = false, includeLocal = false).map { it.name } +
            file.interfaces(includeNested = false).map { it.name } +
            file.objects(includeNested = false).map { it.name } +
            file.functions(includeNested = false, includeLocal = false).map { it.name } +
            file.properties(includeNested = false).map { it.name } +
            file.typeAliases.map { it.name }

    /** This import or one of its enclosing declarations (`Outer.Inner`) is in [declarations]. */
    private fun String.declaredIn(declarations: Set<String>): Boolean =
        generateSequence(this) { name -> name.substringBeforeLast('.', "").ifEmpty { null } }
            .any { it in declarations }
}
