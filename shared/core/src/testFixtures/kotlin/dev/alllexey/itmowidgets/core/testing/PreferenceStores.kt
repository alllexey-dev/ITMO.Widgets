package dev.alllexey.itmowidgets.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.HomeLayoutPreferences
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.storage.QrSettingsPreferences
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.storage.SportSignSelectorPreferences
import dev.alllexey.itmowidgets.core.storage.WidgetSettingsPreferences

/** Every per-concern settings store over one preferences file, as the app graph wires them. */
class PreferenceStores(dataStore: DataStore<Preferences> = InMemoryPreferencesDataStore()) {
    val servicesOptIn = ServicesOptInPreferences(dataStore)
    val scheduleChecks = ScheduleCheckPreferences(dataStore)
    val widgetSettings = WidgetSettingsPreferences(dataStore)
    val qrSettings = QrSettingsPreferences(dataStore)
    val sportSignSelectors = SportSignSelectorPreferences(dataStore)
    val markSources = MarkSourcePreferences(dataStore)
    val homeLayout = HomeLayoutPreferences(dataStore)
    val deviceHints = DeviceHintPreferences(dataStore)
    val demoPreferences = DemoPreferences(dataStore)
}
