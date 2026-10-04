package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugRulesTest {

    @Test
    fun `debug code is gated`() {
        // Where debug state is stored or the debug tools are entered, the code checks BuildConfig.DEBUG itself,
        // so a release build neither opens them nor reads an override; the rest only delegates to those places.
        val gates = productionClasses.filter { declaration ->
            val packageName = declaration.packagee?.name.orEmpty()
            val inDebug = packageName.startsWith(CORE_DEBUG_PACKAGE) || packageName.startsWith(FEATURE_DEBUG_PACKAGE)
            declaration.name == "FileAcademicTimeOverrideStore" ||
                inDebug && (
                    declaration.name.startsWith("File") ||
                        declaration.name.endsWith("Fragment") ||
                        // It replaces the real session's refresh token.
                        declaration.name.endsWith("RefreshTokenController")
                    )
        }
        assertEquals(
            setOf(
                "FileAcademicTimeOverrideStore",
                "FileSportScoreOverrideStore",
                "FileSportLessonTemplateStore",
                "DefaultDebugRefreshTokenController",
                "DebugToolsFragment"
            ),
            gates.map { it.name }.toSet()
        )
        gates.assertTrue { "BuildConfig.DEBUG" in it.text }
        productionClasses
            .filter { it.name == "MeFragment" }
            .requireNonEmpty("MeFragment classes")
            .assertTrue { "debugToolsRow.isVisible = BuildConfig.DEBUG" in it.text }
    }

    private companion object {
        const val CORE_DEBUG_PACKAGE = "dev.alllexey.itmowidgets.core.debug"
        const val FEATURE_DEBUG_PACKAGE = "dev.alllexey.itmowidgets.feature.debug"
    }
}
