package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import kotlinx.coroutines.flow.Flow

/** Which mark sources the background check reads, and the BARS sign-in prompt. Settings of the device. */
class MarkSourcePreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    /** The background check of My ITMO marks; on unless the user turned it off. */
    suspend fun getMyItmoMarksEnabled(): Boolean = read()[MYITMO_MARKS_ENABLED] ?: true

    fun observeMyItmoMarksEnabled(): Flow<Boolean> = observe { it[MYITMO_MARKS_ENABLED] ?: true }

    suspend fun setMyItmoMarksEnabled(enabled: Boolean) {
        write(MYITMO_MARKS_ENABLED, enabled)
    }

    /** The background check of the connected sheets' totals; on unless the user turned it off. */
    suspend fun getSheetMarksEnabled(): Boolean = read()[SHEET_MARKS_ENABLED] ?: true

    fun observeSheetMarksEnabled(): Flow<Boolean> = observe { it[SHEET_MARKS_ENABLED] ?: true }

    suspend fun setSheetMarksEnabled(enabled: Boolean) {
        write(SHEET_MARKS_ENABLED, enabled)
    }

    /** The background check of BARS marks; null (the switch is hidden) until the account's first BARS answer. */
    suspend fun getBarsMarksEnabled(): Boolean? = read()[BARS_MARKS_ENABLED]

    fun observeBarsMarksEnabled(): Flow<Boolean?> = observe { it[BARS_MARKS_ENABLED] }

    suspend fun setBarsMarksEnabled(enabled: Boolean) {
        write(BARS_MARKS_ENABLED, enabled)
    }

    /** Turns BARS marks on when the user has never decided; true when this call changed it. One transaction. */
    suspend fun enableBarsMarksIfUnset(): Boolean {
        var changed = false
        dataStore.edit {
            if (it[BARS_MARKS_ENABLED] == null) {
                it[BARS_MARKS_ENABLED] = true
                changed = true
            }
        }
        return changed
    }

    /** Missing or unknown values are [BarsLoginPrompt.NONE]. */
    suspend fun getBarsLoginPrompt(): BarsLoginPrompt = safeEnumOf(read()[BARS_MARKS_PROMPT], BarsLoginPrompt.NONE)

    suspend fun setBarsLoginPrompt(prompt: BarsLoginPrompt) {
        write(BARS_MARKS_PROMPT, prompt.name)
    }

    /** Forgets the BARS switch and the sign-in prompt with the BARS session; My ITMO's switch stays. */
    suspend fun clearBarsMarkState() {
        dataStore.edit {
            it.remove(BARS_MARKS_ENABLED)
            it.remove(BARS_MARKS_PROMPT)
        }
    }

    private companion object {
        val MYITMO_MARKS_ENABLED = booleanPreferencesKey("myitmo_marks_enabled")
        val BARS_MARKS_ENABLED = booleanPreferencesKey("bars_marks_enabled")
        val SHEET_MARKS_ENABLED = booleanPreferencesKey("sheet_marks_enabled")
        val BARS_MARKS_PROMPT = stringPreferencesKey("bars_marks_prompt")
    }
}
