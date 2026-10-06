package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** Teacher reviews (L15): its rules over `feature.reviews` in `:app` and `:shared:feature-reviews`. */
class ReviewsRulesTest {

    @Test
    fun `the shared reviews code imports no other feature`() {
        // Resources included: the two L15 features share only core types, so either can move to iOS alone.
        productionFiles
            .filter { it.projectPath.contains(COMMON_MAIN) }
            .requireAtLeast(MIN_COMMON_FILES, "reviews commonMain files")
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith(FEATURE_PREFIX) && !import.name.startsWith("$REVIEWS_PACKAGE.")
                }
            }
    }

    private companion object {
        const val COMMON_MAIN = "shared/feature-reviews/src/commonMain/"
        const val FEATURE_PREFIX = "dev.alllexey.itmowidgets.feature."
        const val REVIEWS_PACKAGE = "dev.alllexey.itmowidgets.feature.reviews"

        /**
         * The presentation and Koin files LX-2c moved and the data KM-11f moved;
         * drops only with the integrator's OK.
         */
        const val MIN_COMMON_FILES = 12
    }
}
