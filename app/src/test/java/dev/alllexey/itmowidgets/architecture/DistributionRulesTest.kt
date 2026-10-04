package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.moduleFile
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Assert.assertEquals
import org.junit.Test

class DistributionRulesTest {

    @Test
    fun `distribution variants take the download address from BuildConfig`() {
        val variantFiles = productionFiles.filter { it.sourceSetName == "github" || it.sourceSetName == "play" }
        assertEquals(setOf("github", "play"), variantFiles.map { it.sourceSetName }.toSet())
        // The GitHub releases page is BuildConfig.DOWNLOAD_URL of the github variant only; the play variant
        // must not offer it (Play allows updates only through Play).
        productionFiles.requireNonEmpty("production files").assertFalse { file ->
            "latest_release_url" in file.text || GITHUB_RELEASES in file.text
        }
        val strings = moduleFile(APP_MODULE, "src/main/res/values")
            .listFiles { file -> file.name.matches(Regex("strings.*\\.xml")) }
            .orEmpty()
            .toList()
            .requireNonEmpty("string resource files")
        org.junit.Assert.assertFalse(strings.any { GITHUB_RELEASES in it.readText() })
    }

    private companion object {
        const val GITHUB_RELEASES = "github.com/alllexey-dev/ITMO.Widgets/releases"
    }
}
