package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class ThreadingRulesTest {

    @Test
    fun `code outside core coroutines and di injects AppDispatchers instead of Dispatchers IO`() {
        // di/CoroutinesModule binds AppDispatchers.io to Dispatchers.IO once; tests put every slot on one dispatcher.
        productionFiles
            .filterNot { file ->
                val packageName = file.packagee?.name.orEmpty()
                listOf(CORE_COROUTINES_PACKAGE, DI_PACKAGE).any { packageName == it || packageName.startsWith("$it.") }
            }
            .requireAtLeast(MIN_DOMAIN_FILES, "files outside core.coroutines and di")
            .assertFalse { "Dispatchers.IO" in it.text }
    }

    private companion object {
        const val CORE_COROUTINES_PACKAGE = "dev.alllexey.itmowidgets.core.coroutines"
    }
}
