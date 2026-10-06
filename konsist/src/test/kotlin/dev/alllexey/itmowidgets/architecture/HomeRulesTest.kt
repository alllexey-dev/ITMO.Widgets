package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** The home feed (L09): its rules over `feature.home` in `:app` and `:shared:feature-home`. */
class HomeRulesTest {

    @Test
    fun `the home feed UI parses no dates or times`() {
        // Rows arrive formatted in the academic zone (HomeCardFormatter); the UI only draws strings. Preview samples
        // under preview/ build synthetic cards through the formatter and are exempt.
        productionFiles
            .filter { file -> file.packagee?.name.orEmpty().let { it == UI_PACKAGE || it.startsWith("$UI_PACKAGE.") } }
            .filterNot { PREVIEW_DIRECTORY in it.projectPath }
            .requireAtLeast(MIN_UI_FILES, "home feed UI files")
            .assertFalse { file ->
                file.imports.any { import -> FORBIDDEN_IMPORTS.any { import.name.startsWith(it) } }
            }
    }

    private companion object {
        const val UI_PACKAGE = "dev.alllexey.itmowidgets.feature.home.ui"
        const val PREVIEW_DIRECTORY = "/preview/"
        val FORBIDDEN_IMPORTS = listOf("java.time.", "kotlinx.datetime.")

        /** `HomeFragment`, `HomeRoute`, `HomeScreen`, `HomeCards` and their previews after LH-2. */
        const val MIN_UI_FILES = 5
    }
}
