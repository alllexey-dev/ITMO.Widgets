package dev.alllexey.itmowidgets.architecture

import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionClasses
import dev.alllexey.itmowidgets.architecture.ArchitectureScope.productionFiles
import org.junit.Assert.assertEquals
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
        // Named files, not a *Preferences.kt glob: core/storage/AppPreferences.kt is the DataStore qualifier.
        val stores = productionFiles.filter { it.nameWithExtension in DATA_STORE_FILES }
        assertEquals(
            "A per-concern store was renamed or removed; update the list",
            DATA_STORE_FILES,
            stores.map { it.nameWithExtension }.toSet()
        )
        stores.assertTrue { file -> "DataStore<Preferences>" in file.text }
    }

    private companion object {
        /** The per-concern settings stores AA-07 split from AppSettingsStorage, over one `app_preferences` DataStore. */
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
        val DATA_STORE_FILES = SETTINGS_STORE_FILES + "UtilityStorage.kt" + "DataStoreFriendSelectionHistory.kt"
    }
}
