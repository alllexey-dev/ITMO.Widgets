package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Test

class StorageRulesTest {

    @Test
    fun `session-cleared repositories are singletons`() {
        productionClasses
            .filter { it.name.endsWith("RepositoryImpl") && it.hasParentWithName("SessionDataCleaner") }
            .requireNonEmpty("session-cleared repositories")
            .assertTrue { it.hasAnnotationWithName("Singleton") }
    }

    @Test
    fun `production code does not use legacy preferences`() {
        productionFiles.requireNonEmpty("production files").assertFalse { file ->
            "SharedPreferences" in file.text ||
                "PreferenceManager" in file.text ||
                "SharedPreferencesMigration" in file.text
        }
    }

    @Test
    fun `settings utility and friend history use DataStore`() {
        productionFiles
            .filter {
                it.nameWithExtension in SETTINGS_STORE_FILES ||
                    it.nameWithExtension == "UtilityStorage.kt" ||
                    it.nameWithExtension == "DataStoreFriendSelectionHistory.kt"
            }
            .requireNonEmpty("settings, utility and friend history stores")
            .assertTrue { file ->
                "DataStore<Preferences>" in file.text
            }
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
    }
}
