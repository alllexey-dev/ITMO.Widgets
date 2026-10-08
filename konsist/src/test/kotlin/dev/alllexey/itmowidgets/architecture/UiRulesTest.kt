package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import org.junit.Test

class UiRulesTest {

    @Test
    fun `fragments are hosts without a View binding`() {
        // LR-4b ported the last Fragment that inflated a ViewBinding: every screen is Compose behind its host, so no
        // Fragment may hold a nullable binding or inflate or bind one again. The floor is any Fragment at all, since
        // the shell (L17 SH-1d) removes hosts on purpose.
        productionClasses
            .filter { it.name.endsWith("Fragment") }
            .requireNonEmpty("fragments")
            .assertFalse { fragment -> "_binding" in fragment.text || VIEW_BINDING_CALL.containsMatchIn(fragment.text) }
    }

    private companion object {
        val VIEW_BINDING_CALL = Regex("""\w+Binding\.(inflate|bind)\(""")
    }
}
