package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    private val productionScope = Konsist.scopeFromProduction()

    @Test
    fun `domain does not gain new Android or transport dependencies`() {
        productionScope.files
            .filter { it.packagee?.name?.contains(".domain") == true }
            .filterNot { it.sourcePath() in legacyDomainLeakAllowlist }
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenDomainImportPrefixes.any(import.name::startsWith)
                }
            }
    }

    @Test
    fun `ui does not depend on data implementations`() {
        productionScope.files
            .filter { it.packagee?.name?.contains(".ui") == true }
            .assertFalse { file ->
                file.imports.any { it.name.contains(".data.") }
            }
    }

    @Test
    fun `sport presentation does not depend on transport clients or DTOs`() {
        productionScope.files
            .filter { it.packagee?.name?.startsWith("$SPORT_PACKAGE_PREFIX.") == true }
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenPresentationImportPrefixes.any(import.name::startsWith)
                }
            }
    }

    @Test
    fun `features do not gain new cross feature dependencies`() {
        productionScope.files
            .filter { it.packagee?.name?.startsWith(FEATURE_PACKAGE_PREFIX) == true }
            .assertFalse { file ->
                val sourceFeature = file.packagee
                    ?.name
                    ?.removePrefix(FEATURE_PACKAGE_PREFIX)
                    ?.substringBefore(".")
                    ?: return@assertFalse false

                file.imports.any { import ->
                    val targetFeature = import.name
                        .takeIf { it.startsWith(FEATURE_PACKAGE_PREFIX) }
                        ?.removePrefix(FEATURE_PACKAGE_PREFIX)
                        ?.substringBefore(".")
                        ?: return@any false

                    sourceFeature != targetFeature &&
                        "$sourceFeature->$targetFeature" !in legacyCrossFeatureAllowlist
                }
            }
    }

    @Test
    fun `fragments with nullable binding clear it in onDestroyView`() {
        productionScope.classes()
            .filter { it.name.endsWith("Fragment") }
            .filter { "_binding" in it.text }
            .assertTrue { fragment ->
                "override fun onDestroyView()" in fragment.text &&
                    "_binding = null" in fragment.text
            }
    }

    @Test
    fun `academic code does not gain direct system time calls`() {
        productionScope.files
            .filter { file ->
                val packageName = file.packagee?.name.orEmpty()
                packageName.contains(".feature.") || packageName.contains(".data.")
            }
            .filterNot { it.sourcePath() in legacyDirectTimeAllowlist }
            .assertFalse { file ->
                directSystemTimeCalls.any(file.text::contains)
            }
    }

    @Test
    fun `repository implementations stay in data and implement contracts`() {
        productionScope.classes()
            .filter { it.name.endsWith("RepositoryImpl") }
            .assertTrue { repository ->
                repository.packagee?.name?.contains(".data.") == true &&
                    repository.hasParent { it.name.endsWith("Repository") }
            }
    }

    private fun com.lemonappdev.konsist.api.declaration.KoFileDeclaration.sourcePath(): String {
        return path.substringAfter("app/src/main/java/")
    }

    private companion object {
        const val FEATURE_PACKAGE_PREFIX =
            "dev.alllexey.itmowidgets.feature."
        const val SPORT_PACKAGE_PREFIX =
            "dev.alllexey.itmowidgets.feature.sport"

        val forbiddenDomainImportPrefixes = listOf(
            "android.",
            "api.myitmo.",
            "dev.alllexey.itmowidgets.core.model."
        )

        val legacyDomainLeakAllowlist = setOf(
            "dev/alllexey/itmowidgets/domain/repository/QrBitmapCache.kt"
        )

        val forbiddenPresentationImportPrefixes = listOf(
            "api.myitmo.",
            "dev.alllexey.itmowidgets.core.model.",
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.data."
        )

        val legacyCrossFeatureAllowlist = setOf(
            "schedule->friendselector"
        )

        val legacyDirectTimeAllowlist = setOf(
            "dev/alllexey/itmowidgets/data/local/QrCodeLocalDataSourceImpl.kt",
            "dev/alllexey/itmowidgets/data/local/ScheduleLocalDataSourceImpl.kt",
            "dev/alllexey/itmowidgets/feature/qr/util/QrBitmapRenderer.kt"
        )

        val directSystemTimeCalls = listOf(
            "LocalDate.now(",
            "OffsetDateTime.now(",
            "Calendar.getInstance(",
            "System.currentTimeMillis("
        )
    }
}
