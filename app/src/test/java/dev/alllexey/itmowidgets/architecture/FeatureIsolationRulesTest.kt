package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
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
}
