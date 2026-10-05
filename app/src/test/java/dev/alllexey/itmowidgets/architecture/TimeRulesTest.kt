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

    private companion object {
        const val CORE_TIME_PACKAGE = "dev.alllexey.itmowidgets.core.time"

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
            Regex("""\bClock\.system\w*\("""),
            Regex("""\bClock\.System\b""")
        )
    }
}
