package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class TimeRulesTest {

    @Test
    fun `feature and storage code use injected time`() {
        productionFiles
            .filter { file ->
                val packageName = file.packagee?.name.orEmpty()
                packageName.startsWith(FEATURE_PACKAGE_PREFIX) ||
                    packageName.startsWith(CORE_STORAGE_PACKAGE)
            }
            .requireNonEmpty("feature and storage files")
            .assertFalse { file ->
                directSystemTimeCalls.any(file.text::contains)
            }
    }

    private companion object {
        val directSystemTimeCalls = listOf(
            "LocalDate.now(",
            "OffsetDateTime.now(",
            "Calendar.getInstance(",
            "System.currentTimeMillis("
        )
    }
}
