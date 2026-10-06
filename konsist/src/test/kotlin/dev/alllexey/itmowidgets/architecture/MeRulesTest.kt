package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class MeRulesTest {

    @Test
    fun `the debug tools row shows only in debug builds`() {
        // The Me screen is the only entry into the debug tools: the shared screen shows the row only when its host
        // says so, and the Android host derives that from the build type.
        productionClasses
            .filter { it.name == "MeFragment" }
            .requireNonEmpty("MeFragment classes")
            .assertTrue { "showDebugTools = BuildConfig.DEBUG" in it.text }
        productionFiles
            .filter { it.name == "MeScreen" && it.path.contains("/commonMain/") }
            .requireNonEmpty("MeScreen files")
            .assertTrue { file ->
                file.functions().any { it.name == "MeScreen" && it.hasParameterWithName("showDebugTools") } &&
                    "if (showDebugTools)" in file.text
            }
    }
}
