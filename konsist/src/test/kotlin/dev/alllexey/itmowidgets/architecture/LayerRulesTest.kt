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
                        CLIENT_LIBRARIES.any(import.name::startsWith) ||
                        FEATURE_OUTER_LAYER.containsMatchIn(import.name) ||
                        (
                            import.name.startsWith(CORE_TRANSPORT_MODELS) &&
                                import.name != SHARED_USER_MODEL
                        )
                }
            }
    }

    @Test
    fun `domain does not use javax inject`() {
        // KMP domain code is wired by the DI module, not annotated; KM-06 moves domain into commonMain.
        Ratchet.assertOnly(RatchetRule.DOMAIN_JAVAX_INJECT, domainFilesImporting { it.startsWith("javax.inject.") })
    }

    @Test
    fun `domain does not use the Java platform`() {
        Ratchet.assertOnly(RatchetRule.DOMAIN_JAVA, domainFilesImporting { it.startsWith("java.") })
    }

    @Test
    fun `ui depends on presentation and domain instead of infrastructure`() {
        productionFiles
            .filter { it.packagee?.name?.contains(".ui") == true }
            .requireNonEmpty("ui files")
            .assertFalse { file ->
                file.imports.any { import ->
                    forbiddenUiImports.any(import.name::startsWith) ||
                        CLIENT_LIBRARIES.any(import.name::startsWith) ||
                        FEATURE_DATA.containsMatchIn(import.name)
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
                    forbiddenPresentationImports.any(import.name::startsWith) ||
                        CLIENT_LIBRARIES.any(import.name::startsWith) ||
                        FEATURE_DATA.containsMatchIn(import.name)
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
    fun `work depends on neither presentation nor another feature's widgets`() {
        // A feature's own work and ui.widget packages may import each other: the widget workers render their widgets.
        productionFiles
            .filter { it.packagee?.name?.split('.')?.contains("work") == true }
            .requireNonEmpty("work files")
            .assertFalse { file ->
                val sourceFeature = featureOf(file.packagee?.name.orEmpty())
                file.imports.any { import ->
                    ".presentation." in import.name ||
                        (".ui.widget." in import.name && featureOf(import.name) != sourceFeature)
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

    /** Domain files with an import matching [forbidden], keyed for the ratchet, with the offending imports. */
    private fun domainFilesImporting(forbidden: (String) -> Boolean): Map<String, String> = productionFiles
        .filter { it.packagee?.name?.contains(".domain") == true }
        .requireAtLeast(MIN_DOMAIN_FILES, "domain files")
        .associate { file -> file.ratchetKey to file.imports.map { it.name }.filter(forbidden) }
        .filterValues { it.isNotEmpty() }
        .mapValues { (_, imports) -> "imports ${imports.joinToString()}" }

    private companion object {
        /** MyItmoApi 1.x (`api.myitmo`, `api.bars`), MyItmoApi 2.x and the Core 2.0 Backend client. */
        val CLIENT_LIBRARIES = listOf(
            "api.",
            "dev.alllexey.itmoapi.",
            "dev.alllexey.itmowidgets.client."
        )
        val FEATURE_DATA = Regex("""^dev\.alllexey\.itmowidgets\.feature\.\w+\.data\.""")
        val FEATURE_OUTER_LAYER =
            Regex("""^dev\.alllexey\.itmowidgets\.feature\.\w+\.(data|presentation|ui|work)\.""")

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
            "com.google.gson.",
            "dev.alllexey.itmowidgets.R",
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.core.storage.",
            "dev.alllexey.itmowidgets.core.ui."
        )

        val forbiddenUiImports = listOf(
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
