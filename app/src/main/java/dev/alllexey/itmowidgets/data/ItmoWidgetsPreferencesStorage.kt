package dev.alllexey.itmowidgets.data

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.alllexey.itmowidgets.core.model.TokenResponse
import dev.alllexey.itmowidgets.core.utils.ItmoWidgetsStorage

class ItmoWidgetsPreferencesStorage(val prefs: SharedPreferences) : ItmoWidgetsStorage {

    override fun setAccessToken(accessToken: String?) {
        prefs.edit(commit = true) {
            putString(ACCESS_TOKEN_KEY, accessToken)
        }
    }

    override fun setAccessExpiresAt(accessExpiresAt: Long) {
        prefs.edit(commit = true) {
            putLong(ACCESS_TOKEN_EXPIRES_KEY, accessExpiresAt)
        }
    }

    override fun setRefreshToken(refreshToken: String?) {
        prefs.edit(commit = true) {
            putString(REFRESH_TOKEN_KEY, refreshToken)
        }
    }

    override fun setRefreshExpiresAt(refreshExpiresAt: Long) {
        prefs.edit(commit = true) {
            putLong(REFRESH_TOKEN_EXPIRES_KEY, refreshExpiresAt)
        }
    }

    override fun getAccessToken(): String? {
        return prefs.getString(ACCESS_TOKEN_KEY, null)
    }

    override fun getAccessExpiresAt(): Long {
        return prefs.getLong(ACCESS_TOKEN_EXPIRES_KEY, 0)
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(REFRESH_TOKEN_KEY, null)
    }

    override fun getRefreshExpiresAt(): Long {
        return prefs.getLong(REFRESH_TOKEN_EXPIRES_KEY, 0)
    }

    override fun updateTokens(response: TokenResponse) {
        prefs.edit(commit = true) {
            putString(ACCESS_TOKEN_KEY, response.accessToken)
                .putLong(
                    ACCESS_TOKEN_EXPIRES_KEY,
                    System.currentTimeMillis() + response.accessTokenExpiresIn
                )
                .putString(REFRESH_TOKEN_KEY, response.refreshToken)
                .putLong(
                    REFRESH_TOKEN_EXPIRES_KEY,
                    System.currentTimeMillis() + response.refreshTokenExpiresIn
                )
        }
    }

    companion object KEYS {
        const val ACCESS_TOKEN_KEY = "itmo_widgets_access_token"
        const val ACCESS_TOKEN_EXPIRES_KEY = ACCESS_TOKEN_KEY + "_expires"
        const val REFRESH_TOKEN_KEY = "itmo_widgets_refresh_token"
        const val REFRESH_TOKEN_EXPIRES_KEY = REFRESH_TOKEN_KEY + "_expires"
    }
}