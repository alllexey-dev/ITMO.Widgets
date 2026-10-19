package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/**
 * What common and iOS source sets of the multiplatform modules may import. The Android-only modules (the Compose kit
 * and strings, [isAndroidOnlyModule]) have no iOS target, so their `commonMain` is Android code. Android `R` is
 * `UiTextRulesTest`'s.
 */
class KmpRulesTest {

    @Test
    fun `common and ios code imports no android, java or javax`() {
        kmpFiles().assertFalse { file -> file.imports.any { import -> PLATFORM_ONLY.any(import.name::startsWith) } }
    }

    @Test
    fun `common and ios code imports androidx only from the multiplatform artifacts`() {
        kmpFiles().assertFalse { file ->
            file.imports.any { import ->
                import.name.startsWith("androidx.") && multiplatformAndroidx.none(import.name::startsWith)
            }
        }
    }

    @Test
    fun `common and ios code imports no Compose`() {
        // iOS draws no Compose (ADR 0031): core and every feature keep their Compose UI in androidMain and read text
        // from the app's string tables on iOS (ADR 0035), so the iOS framework links no Compose.
        kmpFiles().assertFalse { file -> file.imports.any { import -> COMPOSE.any(import.name::startsWith) } }
    }

    private fun kmpFiles(): List<KoFileDeclaration> = productionFiles
        .filter { KMP_SOURCE_SET.containsMatchIn(it.projectPath) && !it.isAndroidOnlyModule() }
        .requireAtLeast(MIN_KMP_SOURCE_FILES, "commonMain and iosMain files of the multiplatform modules")

    private companion object {
        /** `commonMain`, `iosMain` and the per-target `ios*Main` sets. */
        val KMP_SOURCE_SET = Regex("""/src/(common|ios\w*)Main/""")

        val PLATFORM_ONLY = listOf("android.", "java.", "javax.")

        /** Compose UI and resources, Coil's and MaterialKolor's Compose layers, the Compose kit and strings. */
        val COMPOSE = listOf(
            "androidx.compose.",
            "org.jetbrains.compose.",
            "coil3.compose",
            "com.materialkolor.ktx.",
            "dev.alllexey.itmowidgets.designsystem.",
            "dev.alllexey.itmowidgets.shared.compose.strings.",
            "dev.alllexey.itmowidgets.shared.designsystem.",
        )

        /** The androidx artifacts that publish common and iOS variants and carry no Compose. */
        val multiplatformAndroidx = listOf(
            "androidx.lifecycle.",
            "androidx.savedstate.",
            "androidx.datastore.core.",
            "androidx.datastore.preferences.core.",
            "androidx.annotation.",
            "androidx.collection.",
        )
    }
}
