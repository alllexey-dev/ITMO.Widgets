package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class TimeRulesTest {

    @Test
    fun `feature core and app code use injected time`() {
        // core.time builds AcademicTimeProvider and di binds the clocks; everything else asks them.
        // Instant.now(clock) and the like read an injected clock and stay legal (FileAppDiagnostics).
        productionFiles
            .filter { file ->
                val packageName = file.packagee?.name.orEmpty()
                (
                    packageName.startsWith(FEATURE_PACKAGE_PREFIX) ||
                        packageName.startsWith(CORE_PACKAGE_PREFIX) ||
                        packageName.startsWith(APP_PACKAGE_PREFIX)
                    ) &&
                    packageName != CORE_TIME_PACKAGE &&
                    !packageName.startsWith("$CORE_TIME_PACKAGE.")
            }
            .requireAtLeast(MIN_DOMAIN_FILES, "feature, core and app files")
            .assertFalse { file ->
                directSystemTimeCalls.any(file.text::contains) ||
                    systemClockReads.any { it.containsMatchIn(file.text) }
            }
    }

    @Test
    fun `the system clock is read only in core time and di`() {
        // kotlin.time's wall clock is bound once (app di, shared core.di or feature.<x>.di) and injected everywhere
        // else. The test kit of shared/testing is a test dependency only and may default to it.
        productionFiles
            .filterNot { it.projectPath.startsWith(TEST_KIT_MODULE) }
            .requireAtLeast(MIN_DOMAIN_FILES, "production files")
            .assertFalse { file ->
                val packageName = file.packagee?.name.orEmpty()
                val bindsClocks = packageName.isIn(CORE_TIME_PACKAGE) || packageName.isIn(DI_PACKAGE) ||
                    packageName.isIn(CORE_DI_PACKAGE) || FEATURE_DI.matches(packageName)
                !bindsClocks && SYSTEM_CLOCK.containsMatchIn(file.text)
            }
    }

    private fun String.isIn(packageName: String): Boolean = this == packageName || startsWith("$packageName.")

    private companion object {
        const val CORE_TIME_PACKAGE = "dev.alllexey.itmowidgets.core.time"
        const val CORE_DI_PACKAGE = "dev.alllexey.itmowidgets.core.di"
        const val TEST_KIT_MODULE = "/shared/testing/"
        val FEATURE_DI = Regex("""dev\.alllexey\.itmowidgets\.feature\.\w+\.di(\..+)?""")
        val SYSTEM_CLOCK = Regex("""\bClock\.System\b""")

        /** Banned with any argument. */
        val directSystemTimeCalls = listOf(
            "LocalDate.now(",
            "OffsetDateTime.now(",
            "Calendar.getInstance(",
            "System.currentTimeMillis("
        )

        /** The zero-argument system reads and the system clocks themselves. */
        val systemClockReads = listOf(
            Regex("""\b(Instant|LocalTime|LocalDateTime|ZonedDateTime)\.now\(\s*\)"""),
            Regex("""\bClock\.system\w*\(""")
        )
    }
}
