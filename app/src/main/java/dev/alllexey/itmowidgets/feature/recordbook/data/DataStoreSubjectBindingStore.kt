package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** `discipline:subject,discipline:subject` under one key; a damaged value reads as no bindings. */
@Singleton
class DataStoreSubjectBindingStore @Inject constructor(
    @param:AppPreferences private val preferences: DataStore<Preferences>
) : SubjectBindingStore, SessionDataCleaner {

    override suspend fun get(disciplineId: Long): Long? = read()[disciplineId]

    override suspend fun put(disciplineId: Long, subjectId: Long) = write(read() + (disciplineId to subjectId))

    override suspend fun remove(disciplineId: Long) = write(read() - disciplineId)

    override suspend fun clearSessionData() = withContext(Dispatchers.IO) {
        preferences.edit { it.remove(KEY) }
        Unit
    }

    private suspend fun read(): Map<Long, Long> = withContext(Dispatchers.IO) {
        try {
            decode(preferences.data.first()[KEY])
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun write(bindings: Map<Long, Long>) = withContext(Dispatchers.IO) {
        preferences.edit { it[KEY] = bindings.entries.joinToString(",") { (discipline, subject) -> "$discipline:$subject" } }
        Unit
    }

    private fun decode(value: String?): Map<Long, Long> = value.orEmpty().split(",")
        .mapNotNull { entry ->
            val (discipline, subject) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val key = discipline.trim().toLongOrNull() ?: return@mapNotNull null
            val bound = subject.trim().toLongOrNull() ?: return@mapNotNull null
            key to bound
        }
        .toMap()

    private companion object {
        val KEY = stringPreferencesKey("subject_bindings")
    }
}
