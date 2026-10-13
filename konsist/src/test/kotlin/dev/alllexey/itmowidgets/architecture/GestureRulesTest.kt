package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.testFiles
import org.junit.Test

/**
 * The bottom tabs switch by the bar only (design.md "Tab switching"): no code turns a tab into a page, and the
 * system's back edges stay the system's, so its predictive Back always works.
 */
class GestureRulesTest {

    @Test
    fun `no code excludes the system gesture edges`() {
        (productionFiles + testFiles)
            .filter { file -> SCANNED_MODULES.any { file.projectPath.startsWith(it) } }
            .requireAtLeast(MIN_DOMAIN_FILES, "app and shared files")
            .assertFalse { GESTURE_EXCLUSION.containsMatchIn(it.text) }
    }

    @Test
    fun `no code maps a tab to a page`() {
        // Routes, widgets, shortcuts and links name an AppTab, the bar selects one; nothing pages through the tabs.
        val tabCode = productionFiles
            .filter { SHARED_COMMON_MAIN.containsMatchIn(it.projectPath) || it.projectPath.startsWith(APP_MAIN) }
            .filter { APP_TAB.containsMatchIn(it.text) }
            .requireAtLeast(MIN_TAB_FILES, "files that name AppTab")
        tabCode.assertFalse { TAB_INDEX.containsMatchIn(it.text) }

        val shellFiles = productionFiles
            .filter { it.projectPath.startsWith(APP_SHELL) }
            .requireAtLeast(MIN_SHELL_FILES, "app shell files")
        shellFiles.assertFalse { PAGER_READ.containsMatchIn(it.text) }
    }

    private companion object {
        const val APP_MAIN = "/app/src/main/"
        val SCANNED_MODULES = listOf("/app/", "/shared/")
        val SHARED_COMMON_MAIN = Regex("""^/shared/[^/]+/src/commonMain/""")

        const val APP_SHELL = "/app/src/main/java/dev/alllexey/itmowidgets/app/shell/"
        const val MIN_TAB_FILES = 5
        const val MIN_SHELL_FILES = 5
        val APP_TAB = Regex("""\bAppTab\b""")

        /** A tab's ordinal or its index among the entries. */
        val TAB_INDEX = Regex("""\.ordinal\b|\bAppTab\.entries\.indexOf\b""")

        /** The pager state and its page reads. */
        val PAGER_READ = Regex("""\bPagerState\b|\.(currentPage|settledPage|targetPage|currentPageOffsetFraction)\b""")

        /** The View flag, its setter and the Compose modifier. */
        val GESTURE_EXCLUSION = Regex("""(?i)systemGestureExclusion""")
    }
}
