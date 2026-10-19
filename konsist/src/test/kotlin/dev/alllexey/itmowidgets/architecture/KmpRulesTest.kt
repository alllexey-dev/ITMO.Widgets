package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** What common and iOS source sets may import. Android `R` is `UiTextRulesTest`'s. */
class KmpRulesTest {

    @Test
    fun `common and ios code imports no android, java or javax`() {
        kmpFiles().assertFalse { file -> file.imports.any { import -> PLATFORM_ONLY.any(import.name::startsWith) } }
    }

    @Test
    fun `common and ios code imports androidx only from the multiplatform artifacts`() {
        // A blanket androidx ban would break Compose Multiplatform and the multiplatform DataStore core.
        kmpFiles().assertFalse { file ->
            file.imports.any { import ->
                val name = import.name
                name.startsWith("androidx.") &&
                    (multiplatformAndroidx.none(name::startsWith) || androidOnlyCompose.any(name::startsWith))
            }
        }
    }

    @Test
    fun `ios code outside the Compose kit imports no Compose`() {
        // iOS draws no Compose (ADR 0031) and reads text from the app's string tables (ADR 0035). The design system's
        // and the test kit's iOS actuals go with the Compose kit once it leaves commonMain.
        iosFiles().assertFalse { file -> file.imports.any { import -> COMPOSE.any(import.name::startsWith) } }
    }

    private fun iosFiles(): List<KoFileDeclaration> = productionFiles
        .filter { it.isIosOutsideComposeKit() }
        .requireAtLeast(MIN_IOS_SOURCE_FILES, "iosMain files outside the Compose kit")

    private fun kmpFiles(): List<KoFileDeclaration> = productionFiles
        .filter { KMP_SOURCE_SET.containsMatchIn(it.projectPath) }
        .requireAtLeast(MIN_KMP_SOURCE_FILES, "commonMain and iosMain files")

    private companion object {
        /** `commonMain`, `iosMain` and the per-target `ios*Main` sets. */
        val KMP_SOURCE_SET = Regex("""/src/(common|ios\w*)Main/""")

        val PLATFORM_ONLY = listOf("android.", "java.", "javax.")

        /** Compose UI and resources, Coil's Compose layer. */
        val COMPOSE = listOf("androidx.compose.", "org.jetbrains.compose.", "coil3.compose")

        const val MIN_IOS_SOURCE_FILES = 100

        /** The androidx artifacts that publish common and iOS variants. */
        val multiplatformAndroidx = listOf(
            "androidx.compose.",
            "androidx.lifecycle.",
            "androidx.navigation3.",
            "androidx.savedstate.",
            "androidx.datastore.core.",
            "androidx.datastore.preferences.core.",
            "androidx.annotation.",
            "androidx.collection.",
            // graphics-shapes is multiplatform; CMP material3 exposes its RoundedPolygon through MaterialShapes.
            "androidx.graphics.shapes."
        )

        /** Compose APIs that exist only on Android. */
        val androidOnlyCompose = listOf(
            "androidx.compose.ui.platform.LocalContext",
            "androidx.compose.ui.viewinterop."
        )
    }
}
