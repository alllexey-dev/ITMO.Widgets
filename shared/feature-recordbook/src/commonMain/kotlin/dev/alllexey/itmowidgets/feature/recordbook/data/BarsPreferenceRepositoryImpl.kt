package dev.alllexey.itmowidgets.feature.recordbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsTokenStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * BARS in the recordbook, keys `recordbook_bars` (connected) and `recordbook_bars_offer_hidden` (the offer to connect
 * was hidden) of the one `app_preferences` DataStore. Koin holds one instance for the screens, the marks check and
 * sign-out (`recordbookModule`).
 */
class BarsPreferenceRepositoryImpl(
    private val tokens: BarsTokenStore,
    private val preferences: DataStore<Preferences>,
    private val markSources: MarkSourcePreferences,
    private val dispatchers: AppDispatchers
) : BarsPreferenceRepository, SessionDataCleaner {
    override suspend fun isEnabled(): Boolean = read(KEY)

    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> = write(KEY, enabled)

    override suspend fun isOfferHidden(): Boolean = read(OFFER_HIDDEN)

    override suspend fun hideOffer(): AppResult<Unit> = write(OFFER_HIDDEN, true)

    private suspend fun read(key: Preferences.Key<Boolean>): Boolean = withContext(dispatchers.io) {
        try { preferences.data.first()[key] == true }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { false }
    }

    private suspend fun write(key: Preferences.Key<Boolean>, value: Boolean): AppResult<Unit> = withContext(dispatchers.io) {
        try { preferences.edit { it[key] = value }; AppResult.Success(Unit) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { AppResult.Failure(AppError.Unknown()) }
    }

    override suspend fun clearSessionData() = withContext(dispatchers.io) {
        tokens.clear()
        preferences.edit {
            it.remove(KEY)
            it.remove(OFFER_HIDDEN)
        }
        // The BARS marks switch and its sign-in prompt belong to the BARS session of this account.
        markSources.clearBarsMarkState()
    }

    private companion object {
        val KEY = booleanPreferencesKey("recordbook_bars")
        val OFFER_HIDDEN = booleanPreferencesKey("recordbook_bars_offer_hidden")
    }
}
