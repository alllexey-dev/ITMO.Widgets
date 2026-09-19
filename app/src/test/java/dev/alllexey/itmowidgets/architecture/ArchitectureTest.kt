package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureTest {

    // Agent worktrees under .claude/ are separate checkouts, not this app's sources.
    private val productionFiles = Konsist.scopeFromProduction().files.filterNot { "/.claude/" in it.path }
    private val productionClasses = productionFiles.flatMap { it.classes() }

    @Test
    fun `domain is independent from Android and transport details`() {
        productionFiles
            .filter { it.packagee?.name?.contains(".domain") == true }
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
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.contains(".ui.") ||
                        import.name.contains(".presentation.")
                }
            }
    }

    @Test
    fun `features do not depend directly on other features`() {
        productionFiles
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

                    sourceFeature != targetFeature
                }
            }
    }

    @Test
    fun `core does not depend on features`() {
        productionFiles
            .filter { it.packagee?.name?.startsWith(CORE_PACKAGE_PREFIX) == true }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith(FEATURE_PACKAGE_PREFIX) }
            }
    }

    @Test
    fun `legacy global data and domain buckets stay empty`() {
        productionFiles.assertFalse { file ->
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
            .assertTrue { declaration ->
                declaration.packagee?.name?.contains(".presentation") == true
            }
    }

    @Test
    fun `repository implementations stay in data and implement matching contracts`() {
        productionClasses
            .filter { it.name.endsWith("RepositoryImpl") }
            .assertTrue { repository ->
                val contractName = repository.name.removeSuffix("Impl")
                repository.packagee?.name?.contains(".data") == true &&
                    ": $contractName" in repository.text
            }
    }

    @Test
    fun `fragments with nullable binding clear it in onDestroyView`() {
        productionClasses
            .filter { it.name.endsWith("Fragment") }
            .filter { "_binding" in it.text }
            .assertTrue { fragment ->
                "override fun onDestroyView()" in fragment.text &&
                    "_binding = null" in fragment.text
            }
    }

    @Test
    fun `feature and storage code use injected time`() {
        productionFiles
            .filter { file ->
                val packageName = file.packagee?.name.orEmpty()
                packageName.startsWith(FEATURE_PACKAGE_PREFIX) ||
                    packageName.startsWith(CORE_STORAGE_PACKAGE)
            }
            .assertFalse { file ->
                directSystemTimeCalls.any(file.text::contains)
            }
    }

    @Test
    fun `production code does not use legacy preferences`() {
        productionFiles.assertFalse { file ->
            "SharedPreferences" in file.text ||
                "PreferenceManager" in file.text ||
                "SharedPreferencesMigration" in file.text
        }
    }

    @Test
    fun `settings utility and friend history use DataStore`() {
        productionFiles
            .filter {
                it.name == "AppSettingsStorage.kt" ||
                    it.name == "UtilityStorage.kt" ||
                    it.name == "DataStoreFriendSelectionHistory.kt"
            }
            .assertTrue { file ->
                "DataStore<Preferences>" in file.text
            }
    }

    private companion object {
        const val FEATURE_PACKAGE_PREFIX =
            "dev.alllexey.itmowidgets.feature."
        const val CORE_PACKAGE_PREFIX =
            "dev.alllexey.itmowidgets.core."
        const val CORE_STORAGE_PACKAGE =
            "dev.alllexey.itmowidgets.core.storage"
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
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.core.storage.",
            "dev.alllexey.itmowidgets.data."
        )

        val forbiddenPresentationImports = listOf(
            "android.",
            "api.myitmo.",
            "dev.alllexey.itmowidgets.core.network.",
            "dev.alllexey.itmowidgets.core.storage.",
            "dev.alllexey.itmowidgets.data."
        )

        val directSystemTimeCalls = listOf(
            "LocalDate.now(",
            "OffsetDateTime.now(",
            "Calendar.getInstance(",
            "System.currentTimeMillis("
        )
    }
}
