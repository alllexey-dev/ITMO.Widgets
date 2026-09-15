package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppPreferences
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BarsPreferenceRepositoryImpl @Inject constructor(
    private val tokens: BarsTokenStore,
    @param:AppPreferences private val preferences: DataStore<Preferences>
) : BarsPreferenceRepository, SessionDataCleaner {
    override suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        try { preferences.data.first()[KEY] == true }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { false }
    }

    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> = withContext(Dispatchers.IO) {
        try { preferences.edit { it[KEY] = enabled }; AppResult.Success(Unit) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { AppResult.Failure(AppError.Unknown()) }
    }

    override suspend fun clearSessionData() = withContext(Dispatchers.IO) {
        tokens.clear()
        preferences.edit { it.remove(KEY) }
        Unit
    }

    private companion object { val KEY = booleanPreferencesKey("recordbook_bars") }
}
