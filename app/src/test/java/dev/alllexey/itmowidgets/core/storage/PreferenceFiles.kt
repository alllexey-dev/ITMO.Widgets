package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import org.junit.rules.TemporaryFolder

/** A path for a preferences file that does not exist yet. */
internal fun TemporaryFolder.preferencesFile(name: String = "settings.preferences_pb"): File =
    newFile(name).apply { delete() }

/** A real DataStore over [file]; a second one over the same file after [scope] ends reads what survived a restart. */
internal fun fileDataStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
