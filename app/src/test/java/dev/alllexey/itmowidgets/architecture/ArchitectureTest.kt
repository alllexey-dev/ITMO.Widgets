package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchitectureTest {

    // Agent worktrees under .claude/ are separate checkouts, not this app's sources. The project path, not the
    // absolute one: inside such a worktree the absolute path of every file contains /.claude/.
    private val productionFiles = Konsist.scopeFromProduction().files.filterNot { "/.claude/" in it.projectPath }
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
    fun `session-cleared repositories are singletons`() {
        productionClasses
            .filter { it.name.endsWith("RepositoryImpl") && it.hasParentWithName("SessionDataCleaner") }
            .assertTrue { it.hasAnnotationWithName("Singleton") }
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
                it.name in SETTINGS_STORE_FILES ||
                    it.name == "UtilityStorage.kt" ||
                    it.name == "DataStoreFriendSelectionHistory.kt"
            }
            .assertTrue { file ->
                "DataStore<Preferences>" in file.text
            }
    }

    @Test
    fun `network clients are gated by demo mode`() {
        val gated = productionClasses.filter { declaration ->
            declaration.packagee?.name != NETWORK_PACKAGE &&
                declaration.constructors.any { constructor -> constructor.parameters.any(::isNetworkClient) }
        }
        // A rule that matches nothing proves nothing.
        org.junit.Assert.assertTrue("Only ${gated.size} classes take a network client", gated.size > MIN_GATED_CLASSES)
        gated.assertTrue { declaration ->
            declaration.constructors
                .filter { constructor -> constructor.parameters.any(::isNetworkClient) }
                .all { constructor -> constructor.parameters.any { it.type.name == DEMO_MODE } }
        }
    }

    private fun isNetworkClient(parameter: KoParameterDeclaration): Boolean =
        parameter.type.name in networkClients ||
            (parameter.type.name == OK_HTTP_CLIENT && parameter.hasAnnotationWithName(PUBLIC_WEB_CLIENT))

    @Test
    fun `debug code is gated`() {
        // Where debug state is stored or the debug tools are entered, the code checks BuildConfig.DEBUG itself,
        // so a release build neither opens them nor reads an override; the rest only delegates to those places.
        val gates = productionClasses.filter { declaration ->
            val packageName = declaration.packagee?.name.orEmpty()
            val inDebug = packageName.startsWith(CORE_DEBUG_PACKAGE) || packageName.startsWith(FEATURE_DEBUG_PACKAGE)
            declaration.name == "FileAcademicTimeOverrideStore" ||
                inDebug && (
                    declaration.name.startsWith("File") ||
                        declaration.name.endsWith("Fragment") ||
                        // It replaces the real session's refresh token.
                        declaration.name.endsWith("RefreshTokenController")
                    )
        }
        assertEquals(
            setOf(
                "FileAcademicTimeOverrideStore",
                "FileSportScoreOverrideStore",
                "FileSportLessonTemplateStore",
                "DefaultDebugRefreshTokenController",
                "DebugToolsFragment"
            ),
            gates.map { it.name }.toSet()
        )
        gates.assertTrue { "BuildConfig.DEBUG" in it.text }
        productionClasses
            .filter { it.name == "MeFragment" }
            .assertTrue { "debugToolsRow.isVisible = BuildConfig.DEBUG" in it.text }
    }

    @Test
    fun `distribution variants take the download address from BuildConfig`() {
        val variantFiles = productionFiles.filter { it.sourceSetName == "github" || it.sourceSetName == "play" }
        assertEquals(setOf("github", "play"), variantFiles.map { it.sourceSetName }.toSet())
        // The GitHub releases page is BuildConfig.DOWNLOAD_URL of the github variant only; the play variant
        // must not offer it (Play allows updates only through Play).
        productionFiles.assertFalse { file ->
            "latest_release_url" in file.text || GITHUB_RELEASES in file.text
        }
        org.junit.Assert.assertFalse(GITHUB_RELEASES in File("src/main/res/values/strings.xml").readText())
    }

    private companion object {
        val SETTINGS_STORE_FILES = setOf(
            "DataStorePreferences.kt",
            "ServicesOptInPreferences.kt",
            "ScheduleCheckPreferences.kt",
            "WidgetSettingsPreferences.kt",
            "QrSettingsPreferences.kt",
            "SportSignSelectorPreferences.kt",
            "MarkSourcePreferences.kt",
            "HomeLayoutPreferences.kt",
            "DeviceHintPreferences.kt",
            "DemoPreferences.kt"
        )
        const val CORE_DEBUG_PACKAGE = "dev.alllexey.itmowidgets.core.debug"
        const val FEATURE_DEBUG_PACKAGE = "dev.alllexey.itmowidgets.feature.debug"
        const val GITHUB_RELEASES = "github.com/alllexey-dev/ITMO.Widgets/releases"
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
        /** Builds the clients themselves; every user of a client is gated instead. */
        const val NETWORK_PACKAGE =
            "dev.alllexey.itmowidgets.core.network"
        const val DEMO_MODE = "DemoMode"
        const val OK_HTTP_CLIENT = "OkHttpClient"
        const val PUBLIC_WEB_CLIENT = "PublicWebClient"
        const val MIN_GATED_CLASSES = 20

        /** My ITMO, BARS and Backend clients; the public web client is matched by its qualifier. */
        val networkClients = setOf("ItmoWidgetsApi", "MyItmo", "MyItmoApi", "Bars")

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

        val directSystemTimeCalls = listOf(
            "LocalDate.now(",
            "OffsetDateTime.now(",
            "Calendar.getInstance(",
            "System.currentTimeMillis("
        )
    }
}
