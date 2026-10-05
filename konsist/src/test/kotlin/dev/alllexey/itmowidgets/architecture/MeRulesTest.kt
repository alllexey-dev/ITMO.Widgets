package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Test

class MeRulesTest {

    @Test
    fun `the debug tools row shows only in debug builds`() {
        // The Me screen is the only entry into the debug tools; its port rewrites this check.
        productionClasses
            .filter { it.name == "MeFragment" }
            .requireNonEmpty("MeFragment classes")
            .assertTrue { "debugToolsRow.isVisible = BuildConfig.DEBUG" in it.text }
    }
}
