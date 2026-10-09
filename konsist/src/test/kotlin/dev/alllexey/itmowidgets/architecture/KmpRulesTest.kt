package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/**
 * What common and iOS source sets may import (master plan 3.1). Android `R` is `UiTextRulesTest`'s; Hilt and Dagger
 * outside these source sets are `DiRulesTest`'s.
 */
class KmpRulesTest {

    @Test
    fun `common and ios code imports no android java javax or dagger`() {
        kmpFiles().assertFalse { file -> file.imports.any { import -> PLATFORM_ONLY.any(import.name::startsWith) } }
    }

    @Test
    fun `common and ios code imports androidx only from the multiplatform artifacts`() {
        // A blanket androidx ban would break Compose Multiplatform and the DataStore core of KM-04.
        kmpFiles().assertFalse { file ->
            file.imports.any { import ->
                val name = import.name
                name.startsWith("androidx.") &&
                    (multiplatformAndroidx.none(name::startsWith) || androidOnlyCompose.any(name::startsWith))
            }
        }
    }

    private fun kmpFiles(): List<KoFileDeclaration> = productionFiles
        .filter { KMP_SOURCE_SET.containsMatchIn(it.projectPath) }
        .requireAtLeast(MIN_KMP_SOURCE_FILES, "commonMain and iosMain files")

    private companion object {
        /** `commonMain`, `iosMain` and the per-target `ios*Main` sets. */
        val KMP_SOURCE_SET = Regex("""/src/(common|ios\w*)Main/""")

        val PLATFORM_ONLY = listOf("android.", "java.", "javax.", "dagger.")

        /** The KMP artifacts TC-04a declares. */
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
