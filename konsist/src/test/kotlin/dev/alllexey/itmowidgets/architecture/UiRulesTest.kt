package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Test

class UiRulesTest {

    @Test
    fun `fragments with nullable binding clear it in onDestroyView`() {
        // Ports to Compose shrink this set on purpose, so it has no absolute floor.
        productionClasses
            .filter { it.name.endsWith("Fragment") }
            .filter { "_binding" in it.text }
            .requireNonEmpty("fragments with a nullable binding")
            .assertTrue { fragment ->
                "override fun onDestroyView()" in fragment.text &&
                    "_binding = null" in fragment.text
            }
    }
}
