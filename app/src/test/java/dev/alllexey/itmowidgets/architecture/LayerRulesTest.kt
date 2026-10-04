package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class LayerRulesTest {

    @Test
    fun `domain is independent from Android and transport details`() {
        productionFiles
            .filter { it.packagee?.name?.contains(".domain") == true }
            .requireAtLeast(MIN_DOMAIN_FILES, "domain files")
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenDomainImports.any(import.name::startsWith) ||
                        (
                            import.name.startsWith(CORE_TRANSPORT_MODELS) &&
                                import.name != SHARED_USER_MODEL
                        )
                }
            }
    }

    @Test
    fun `ui depends on presentation and domain instead of infrastructure`() {
        productionFiles
            .filter { it.packagee?.name?.contains(".ui") == true }
            .requireNonEmpty("ui files")
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenUiImports.any(import.name::startsWith)
                }
            }
    }

    @Test
    fun `presentation does not depend on data or transport`() {
        productionFiles
            .filter { it.packagee?.name?.contains(".presentation") == true }
            .requireNonEmpty("presentation files")
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenPresentationImports.any(import.name::startsWith)
                } ||
                    "Throwable" in file.text ||
                    ".message" in file.text
            }
    }

    @Test
    fun `data does not depend on ui or presentation`() {
        productionFiles
            .filter { it.packagee?.name?.contains(".data") == true }
            .requireNonEmpty("data files")
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.contains(".ui.") ||
                        import.name.contains(".presentation.")
                }
            }
    }

    @Test
    fun `legacy global data and domain buckets stay empty`() {
        productionFiles.requireNonEmpty("production files").assertFalse { file ->
            val packageName = file.packagee?.name.orEmpty()
            packageName == LEGACY_DATA_PACKAGE ||
                packageName.startsWith("$LEGACY_DATA_PACKAGE.") ||
                packageName == LEGACY_DOMAIN_PACKAGE ||
                packageName.startsWith("$LEGACY_DOMAIN_PACKAGE.")
        }
    }

    @Test
    fun `view models live in presentation packages`() {
        productionClasses
            .filter { declaration ->
                declaration.name.endsWith("ViewModel") &&
                    declaration.text.contains(": ViewModel")
            }
            .requireAtLeast(MIN_VIEW_MODELS, "ViewModel subclasses")
            .assertTrue { declaration ->
                declaration.packagee?.name?.contains(".presentation") == true
            }
    }

    @Test
    fun `repository implementations stay in data and implement matching contracts`() {
        productionClasses
            .filter { it.name.endsWith("RepositoryImpl") }
            .requireNonEmpty("repository implementations")
            .assertTrue { repository ->
                val contractName = repository.name.removeSuffix("Impl")
                repository.packagee?.name?.contains(".data") == true &&
                    ": $contractName" in repository.text
            }
    }

    private companion object {
        const val CORE_TRANSPORT_MODELS =
            "dev.alllexey.itmowidgets.core.model."
        const val SHARED_USER_MODEL =
            "dev.alllexey.itmowidgets.core.model.UserSummary"
        const val LEGACY_DATA_PACKAGE =
            "dev.alllexey.itmowidgets.data"
        const val LEGACY_DOMAIN_PACKAGE =
            "dev.alllexey.itmowidgets.domain"

        val forbiddenDomainImports = listOf(
            "android.",
            "androidx.",
            "api.myitmo.",
            "com.google.gson.",
            "dev.alllexey.itmowidgets.R",
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.core.storage.",
            "dev.alllexey.itmowidgets.core.ui."
        )

        val forbiddenUiImports = listOf(
            "api.myitmo.",
            "dev.alllexey.itmowidgets.core.model.reviews.",
            "dev.alllexey.itmowidgets.core.model.resources.",
            "dev.alllexey.itmowidgets.core.model.social.",
            "dev.alllexey.itmowidgets.core.model.fcm.",
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.core.storage.",
            "dev.alllexey.itmowidgets.data."
        )

        val forbiddenPresentationImports = listOf(
            "android.",
            "api.myitmo.",
            "dev.alllexey.itmowidgets.core.model.reviews.",
            "dev.alllexey.itmowidgets.core.model.resources.",
            "dev.alllexey.itmowidgets.core.model.social.",
            "dev.alllexey.itmowidgets.core.model.fcm.",
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.core.storage.",
            "dev.alllexey.itmowidgets.data."
        )
    }
}
