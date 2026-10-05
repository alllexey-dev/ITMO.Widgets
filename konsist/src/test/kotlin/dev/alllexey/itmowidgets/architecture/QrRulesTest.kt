package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** The QR pass (L09): its rules over `feature.qr` in `:app` and `:shared:feature-qr`. */
class QrRulesTest {

    @Test
    fun `the QR pass never reads academic time`() {
        // The pass expires on the wall clock: the debug academic-time override must not move its expiry.
        productionFiles
            .filter { file -> file.packagee?.name.orEmpty().let { it == QR_PACKAGE || it.startsWith("$QR_PACKAGE.") } }
            .requireAtLeast(MIN_QR_FILES, "QR pass files")
            .assertFalse { file ->
                file.imports.any { it.name == ACADEMIC_TIME_PROVIDER } || "AcademicTimeProvider" in file.text
            }
    }

    private companion object {
        const val QR_PACKAGE = "dev.alllexey.itmowidgets.feature.qr"
        const val ACADEMIC_TIME_PROVIDER = "dev.alllexey.itmowidgets.core.time.AcademicTimeProvider"

        /** The QR files of `:app` and `:shared:feature-qr` after KM-11g1; drops only with the integrator's OK. */
        const val MIN_QR_FILES = 35
    }
}
