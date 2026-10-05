package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Test

class DebugRulesTest {

    @Test
    fun `debug code is gated`() {
        // Where debug state is stored or the debug tools are entered, the code checks BuildConfig.DEBUG itself,
        // so a release build neither opens them nor reads an override; the rest only delegates to those places.
        productionClasses
            .filter { declaration ->
                val packageName = declaration.packagee?.name.orEmpty()
                val inDebug =
                    packageName.startsWith(CORE_DEBUG_PACKAGE) || packageName.startsWith(FEATURE_DEBUG_PACKAGE)
                declaration.name == "FileAcademicTimeOverrideStore" ||
                    inDebug && (
                        declaration.name.startsWith("File") ||
                            declaration.name.endsWith("Fragment") ||
                            // It replaces the real session's refresh token.
                            declaration.name.endsWith("RefreshTokenController")
                        )
            }
            .requireAtLeast(MIN_DEBUG_ONLY_CLASSES, "debug state stores and debug screens")
            .assertTrue { it.hasAnnotationWithName(DEBUG_ONLY) }
        productionClasses
            .filter { it.hasAnnotationWithName(DEBUG_ONLY) }
            .requireAtLeast(MIN_DEBUG_ONLY_CLASSES, "@$DEBUG_ONLY classes")
            .assertTrue { "BuildConfig.DEBUG" in it.text || it.isShared && it.checksDebugBuild }
    }

    private val KoClassDeclaration.isShared: Boolean get() = containingFile.projectPath.startsWith("/shared/")

    /** Shared code has no BuildConfig; it takes KM-04's injected `core.debug.DebugBuild` and reads its flag instead. */
    private val KoClassDeclaration.checksDebugBuild: Boolean
        get() = ".isDebug" in text &&
            constructors.any { constructor -> constructor.parameters.any { it.type.name == DEBUG_BUILD } }

    private companion object {
        const val DEBUG_BUILD = "DebugBuild"
        const val CORE_DEBUG_PACKAGE = "dev.alllexey.itmowidgets.core.debug"
        const val FEATURE_DEBUG_PACKAGE = "dev.alllexey.itmowidgets.feature.debug"
        const val DEBUG_ONLY = "DebugOnly"
    }
}
