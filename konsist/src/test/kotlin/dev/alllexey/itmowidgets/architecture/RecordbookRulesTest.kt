package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

/** The recordbook (L12): its rules over `feature.recordbook` in `:app` and `:shared:feature-recordbook`. */
class RecordbookRulesTest {

    private val recordbookFiles by lazy {
        productionFiles.filter { file ->
            file.packagee?.name.orEmpty().let { it == RECORDBOOK_PACKAGE || it.startsWith("$RECORDBOOK_PACKAGE.") }
        }
    }

    @Test
    fun `only the app's recordbook code touches WebView and CookieManager`() {
        // The BARS sign-in page, the silent BARS login and the ITMO.ID cookie port stay Android (ADR 0012); the shared
        // module reaches them through ports, so iOS can supply its own.
        recordbookFiles
            .filter { file -> file.imports.any { it.name in WEB_VIEW_TYPES || it.name == WEBKIT_WILDCARD } }
            .requireNonEmpty("recordbook WebView and CookieManager users")
            .assertTrue { file -> file.projectPath.startsWith(APP_SOURCES) }
    }

    @Test
    fun `the shared recordbook module imports no legacy MyItmoApi type`() {
        // MyItmoApi 1.x (`api.*`) is Android-only and leaves with KM-10b; the module talks to MyITMO and BARS through
        // its domain ports only.
        recordbookFiles
            .filter { file -> file.projectPath.startsWith(SHARED_MODULE) }
            .requireAtLeast(MIN_SHARED_RECORDBOOK_FILES, "shared recordbook files")
            .assertFalse { file -> file.imports.any { it.name.startsWith(LEGACY_API_PREFIX) } }
    }

    private companion object {
        const val RECORDBOOK_PACKAGE = "dev.alllexey.itmowidgets.feature.recordbook"
        const val APP_SOURCES = "/app/src/"
        const val SHARED_MODULE = "/shared/feature-recordbook/"
        const val LEGACY_API_PREFIX = "api."
        const val WEBKIT_WILDCARD = "android.webkit.*"
        val WEB_VIEW_TYPES = setOf("android.webkit.WebView", "android.webkit.CookieManager")

        /** The domain, presentation and Koin module files LR-2a moved; drops only with the integrator's OK. */
        const val MIN_SHARED_RECORDBOOK_FILES = 48
    }
}
