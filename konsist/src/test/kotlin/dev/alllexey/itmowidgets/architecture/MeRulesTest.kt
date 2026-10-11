package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class MeRulesTest {

    @Test
    fun `the debug tools row shows only in debug builds`() {
        // The Me screen is the only entry into the debug tools: the shared screen shows the row only when its host
        // says so, and the Android host (the shell's Me tab root) derives that from the build type.
        productionFiles
            .filter { it.name == "AccountEntries" && it.packagee?.name == SHELL_ENTRIES_PACKAGE }
            .requireNonEmpty("AccountEntries files")
            .assertTrue { "showDebugTools = BuildConfig.DEBUG" in it.text }
        productionFiles
            .filter { it.name == "MeScreen" && it.path.contains("/commonMain/") }
            .requireNonEmpty("MeScreen files")
            .assertTrue { file ->
                file.functions().any { it.name == "MeScreen" && it.hasParameterWithName("showDebugTools") } &&
                    "if (showDebugTools)" in file.text
            }
    }

    private companion object {
        const val SHELL_ENTRIES_PACKAGE = "dev.alllexey.itmowidgets.app.shell.entries"
    }
}
