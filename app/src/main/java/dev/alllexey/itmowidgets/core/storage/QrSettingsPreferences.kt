package dev.alllexey.itmowidgets.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.alllexey.itmowidgets.core.settings.QrAnimationType
import dev.alllexey.itmowidgets.core.util.safeEnumOf
import kotlinx.coroutines.flow.Flow

/** How the QR pass looks: dynamic colors, the spoiler and its animation. */
class QrSettingsPreferences(dataStore: DataStore<Preferences>) : DataStorePreferences(dataStore) {

    suspend fun getQrDynamicColorsEnabled(): Boolean = read()[QR_DYNAMIC_COLORS_ENABLED] ?: true

    fun observeQrDynamicColorsEnabled(): Flow<Boolean> = observe { it[QR_DYNAMIC_COLORS_ENABLED] ?: true }

    suspend fun setQrDynamicColorsEnabled(enabled: Boolean) {
        write(QR_DYNAMIC_COLORS_ENABLED, enabled)
    }

    suspend fun getQrSpoilerEnabled(): Boolean = read()[QR_SPOILER_ENABLED] ?: true

    fun observeQrSpoilerEnabled(): Flow<Boolean> = observe { it[QR_SPOILER_ENABLED] ?: true }

    suspend fun setQrSpoilerEnabled(enabled: Boolean) {
        write(QR_SPOILER_ENABLED, enabled)
    }

    suspend fun getQrSpoilerAnimationType(): QrAnimationType = read().animationType()

    fun observeQrSpoilerAnimationType(): Flow<QrAnimationType> = observe { it.animationType() }

    suspend fun setQrSpoilerAnimationType(type: QrAnimationType) {
        write(QR_SPOILER_ANIMATION_TYPE, type.name)
    }

    private fun Preferences.animationType(): QrAnimationType =
        safeEnumOf(this[QR_SPOILER_ANIMATION_TYPE], QrAnimationType.CIRCLE)

    private companion object {
        val QR_DYNAMIC_COLORS_ENABLED = booleanPreferencesKey("qr_dynamic_colors_enabled")
        val QR_SPOILER_ENABLED = booleanPreferencesKey("qr_spoiler_enabled")
        val QR_SPOILER_ANIMATION_TYPE = stringPreferencesKey("qr_spoiler_animation_type")
    }
}
