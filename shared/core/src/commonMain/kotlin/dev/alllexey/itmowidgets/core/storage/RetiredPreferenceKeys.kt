package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

/**
 * Drops keys of `app_preferences` that no feature reads any more, once: after they are gone [shouldMigrate] is false,
 * so the migration costs one check per process. Add a retired key's name to [NAMES] when its store is deleted.
 */
object RetiredPreferenceKeys : DataMigration<Preferences> {

    /** `recent_schedule_friends`: the recent friends of the old friend selector (the schedule's picker since 2.3). */
    private val NAMES = setOf("recent_schedule_friends")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.asMap().keys.any { it.name in NAMES }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply { asMap().keys.filter { it.name in NAMES }.forEach { remove(it) } }

    override suspend fun cleanUp() = Unit
}
