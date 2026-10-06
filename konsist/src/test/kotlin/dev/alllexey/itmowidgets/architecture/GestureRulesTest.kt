package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.testFiles
import org.junit.Test

/**
 * The horizontal swipe between the bottom tabs (ADR 0020 amendment, design.md "Tab swipe"): inner horizontal content
 * hands the swipe over through the design system's gesture modifiers, and the system's back edges stay the system's.
 */
class GestureRulesTest {

    @Test
    fun `horizontal scrollers hand the tab swipe over or block it`() {
        // Empty until the first port with a horizontal scroller merges; the scope itself must not be.
        val scrollers = composeSources()
            .requireNonEmpty("shared commonMain and app main files")
            .filter { HORIZONTAL_SCROLLER.containsMatchIn(it.withoutPreviews()) }
        if (scrollers.isEmpty()) return
        scrollers.assertTrue { TAB_SWIPE_MODIFIER.containsMatchIn(it.withoutPreviews()) }
    }

    @Test
    fun `no code excludes the system gesture edges`() {
        (productionFiles + testFiles)
            .filter { file -> SCANNED_MODULES.any { file.projectPath.startsWith(it) } }
            .requireAtLeast(MIN_DOMAIN_FILES, "app and shared files")
            .assertFalse { GESTURE_EXCLUSION.containsMatchIn(it.text) }
    }

    @Test
    fun `only the tab pager maps a tab to a page`() {
        // Routes, widgets, shortcuts and links name an AppTab; the page index of a tab exists in the tab pager only.
        val tabCode = productionFiles
            .filter { SHARED_COMMON_MAIN.containsMatchIn(it.projectPath) || it.projectPath.startsWith(APP_MAIN) }
            .filter { APP_TAB.containsMatchIn(it.text) }
            .requireAtLeast(MIN_TAB_FILES, "files that name AppTab")
            .filterNot { it.projectPath in TAB_PAGER_FILES }
        tabCode.assertFalse { TAB_INDEX.containsMatchIn(it.text) }

        val shellFiles = productionFiles
            .filter { it.projectPath.startsWith(APP_SHELL) }
            .requireAtLeast(MIN_SHELL_FILES, "app shell files")
            .filterNot { it.projectPath in TAB_PAGER_FILES }
        shellFiles.assertFalse { PAGER_READ.containsMatchIn(it.text) }
    }

    /**
     * Production Compose code of every shared module and the app, minus the kit's gesture package (it names the
     * scrollers it wraps), `preview/` directories and the tab pager itself.
     */
    private fun composeSources(): List<KoFileDeclaration> = productionFiles
        .filter { SHARED_COMMON_MAIN.containsMatchIn(it.projectPath) || it.projectPath.startsWith(APP_MAIN) }
        .filterNot { file -> EXEMPT.any { it in file.projectPath } }

    /** The file's text without its `@Preview` functions. */
    private fun KoFileDeclaration.withoutPreviews(): String =
        functions(includeNested = true, includeLocal = false)
            .filter { it.hasAnnotationWithName(PREVIEW) }
            .fold(text) { code, preview -> code.replace(preview.text, "") }

    private companion object {
        const val PREVIEW = "Preview"
        const val APP_MAIN = "/app/src/main/"
        val SCANNED_MODULES = listOf("/app/", "/shared/")
        val SHARED_COMMON_MAIN = Regex("""^/shared/[^/]+/src/commonMain/""")
        val EXEMPT = listOf(
            "/shared/designsystem/src/commonMain/kotlin/dev/alllexey/itmowidgets/designsystem/gesture/",
            "/preview/",
            "/app/shell/TabPager.kt",
        )

        /** `HorizontalPager(`, `LazyRow(` or `LazyRow {`, `LazyHorizontalGrid(` and `.horizontalScroll(`. */
        val HORIZONTAL_SCROLLER =
            Regex("""\b(HorizontalPager|LazyHorizontalGrid)\s*\(|\bLazyRow\s*[({]|\.horizontalScroll\s*\(""")
        val TAB_SWIPE_MODIFIER = Regex("""\btabSwipe(Handover|Blocked)\s*\(""")

        const val APP_SHELL = "/app/src/main/java/dev/alllexey/itmowidgets/app/shell/"
        const val MIN_TAB_FILES = 5
        const val MIN_SHELL_FILES = 5
        val TAB_PAGER_FILES = listOf("${APP_SHELL}TabPages.kt", "${APP_SHELL}TabPager.kt")
        val APP_TAB = Regex("""\bAppTab\b""")

        /** A tab's ordinal or its index among the entries. */
        val TAB_INDEX = Regex("""\.ordinal\b|\bAppTab\.entries\.indexOf\b""")

        /** The pager state and its page reads. */
        val PAGER_READ = Regex("""\bPagerState\b|\.(currentPage|settledPage|targetPage|currentPageOffsetFraction)\b""")

        /** The View flag, its setter and the Compose modifier. */
        val GESTURE_EXCLUSION = Regex("""(?i)systemGestureExclusion""")
    }
}
