package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** Subject links (L15): its rules over `feature.resources` in `:app` and `:shared:feature-resources`. */
class ResourcesRulesTest {

    @Test
    fun `the shared links code imports no other feature`() {
        // Reviews included: the two L15 features share only core types, so either can move to iOS alone.
        productionFiles
            .filter { it.projectPath.contains(COMMON_MAIN) }
            .requireAtLeast(MIN_COMMON_FILES, "resources commonMain files")
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith(FEATURE_PREFIX) && !import.name.startsWith("$RESOURCES_PACKAGE.")
                }
            }
    }

    private companion object {
        const val COMMON_MAIN = "shared/feature-resources/src/commonMain/"
        const val FEATURE_PREFIX = "dev.alllexey.itmowidgets.feature."
        const val RESOURCES_PACKAGE = "dev.alllexey.itmowidgets.feature.resources"

        /**
         * The domain, presentation and Koin files LX-2b moved and the data KM-11f moved;
         * drops only with the integrator's OK.
         */
        const val MIN_COMMON_FILES = 13
    }
}
