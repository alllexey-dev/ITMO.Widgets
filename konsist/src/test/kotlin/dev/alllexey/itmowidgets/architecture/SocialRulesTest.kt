package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/**
 * Social and the friend picker (L13): rules over `feature.{social,friendselector}` in `:app` and
 * `:shared:feature-social` that `KmpRulesTest`, `FeatureIsolationRulesTest` and `GateRulesTest` do not already cover.
 */
class SocialRulesTest {

    @Test
    fun `only the social data layer reads MyItmoApi personality models`() {
        // A directory entry carries phone and e-mail; the data mappers drop them, so contacts never reach domain,
        // presentation or another feature (ADR 0006).
        productionFiles
            .filter { file -> file.imports.any { it.name.isIn(PERSONALITIES_PACKAGE) } }
            .requireAtLeast(MIN_PERSONALITY_READERS, "MyItmoApi personality model readers")
            .assertTrue { file -> file.packagee?.name.orEmpty().isIn(SOCIAL_DATA_PACKAGE) }
    }

    @Test
    fun `only data mappers and demo data build viewer capabilities`() {
        // UserSharing is Backend's viewer-scoped answer; the client maps it and never derives access itself. Preview
        // samples and debug fixtures build synthetic people and are exempt.
        productionFiles
            .filterNot(::isSynthetic)
            .filter { SHARING_CONSTRUCTOR.containsMatchIn(it.text) }
            .requireNonEmpty("UserSharing builders")
            .assertTrue { file ->
                val pkg = file.packagee?.name.orEmpty()
                FEATURE_DATA_PACKAGE.matches(pkg) || pkg.isIn(CORE_DEMO_PACKAGE) ||
                    (pkg == CORE_MODEL_PACKAGE && file.name == CLIENT_USER_MAPPING)
            }
    }

    private fun isSynthetic(file: KoFileDeclaration): Boolean =
        DEBUG_SOURCES in file.projectPath || PREVIEW_DIRECTORY in file.projectPath ||
            SYNTHETIC_SUFFIXES.any(file.name::endsWith)

    private fun String.isIn(pkg: String): Boolean = this == pkg || startsWith("$pkg.")

    private companion object {
        const val PERSONALITIES_PACKAGE = "dev.alllexey.itmoapi.myitmo.personalities"
        const val SOCIAL_DATA_PACKAGE = "dev.alllexey.itmowidgets.feature.social.data"
        const val CORE_MODEL_PACKAGE = "dev.alllexey.itmowidgets.core.model"
        const val CORE_DEMO_PACKAGE = "dev.alllexey.itmowidgets.core.demo"
        const val CLIENT_USER_MAPPING = "ClientUserMapping"
        const val DEBUG_SOURCES = "/src/debug/"
        const val PREVIEW_DIRECTORY = "/preview/"
        val SYNTHETIC_SUFFIXES = listOf("Samples", "Previews", "PreviewFixtures", "PreviewData")

        /** A call, not the declaration in `core.model.UserSummary`. */
        val SHARING_CONSTRUCTOR = Regex("""(?<!class )\bUserSharing\(""")

        /** A feature's data layer, `DemoSocial` under `feature.social.data.demo` included. */
        val FEATURE_DATA_PACKAGE = Regex("""dev\.alllexey\.itmowidgets\.feature\.[a-z]+\.data(\..+)?""")

        /** `PersonMappers` and `PeopleSearchRepositoryImpl` after KM-10d2; drops only with the integrator's OK. */
        const val MIN_PERSONALITY_READERS = 2
    }
}
