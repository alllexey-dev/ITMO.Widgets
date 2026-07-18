package dev.alllexey.itmowidgets.core.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import api.myitmo.model.other.TokenResponse
import api.myitmo.storage.Storage
import javax.inject.Inject

class MyItmoStorage @Inject constructor(val prefs: SharedPreferences) : Storage {

    companion object KEYS {
        const val ACCESS_TOKEN_KEY = "my_itmo_access_token"
        const val ACCESS_TOKEN_EXPIRES_AT_KEY = ACCESS_TOKEN_KEY + "_expires"
        const val REFRESH_TOKEN_KEY = "my_itmo_refresh_token"
        const val REFRESH_TOKEN_EXPIRES_AT_KEY = REFRESH_TOKEN_KEY + "_expires"
        const val ID_TOKEN_KEY = "my_itmo_id_token"
    }

    override fun getAccessToken(): String? {
        return prefs.getString(ACCESS_TOKEN_KEY, null)
    }

    override fun getAccessExpiresAt(): Long {
        return prefs.getLong(ACCESS_TOKEN_EXPIRES_AT_KEY, 0)
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(REFRESH_TOKEN_KEY, null)
    }

    override fun getRefreshExpiresAt(): Long {
        return prefs.getLong(REFRESH_TOKEN_EXPIRES_AT_KEY, 0)
    }

    override fun getIdToken(): String? {
        return prefs.getString(ID_TOKEN_KEY, null)
    }

    override fun setAccessToken(accessToken: String?) {
        prefs.edit(commit = true) {
            putString(ACCESS_TOKEN_KEY, accessToken)
        }
    }

    override fun setAccessExpiresAt(accessExpiresAt: Long) {
        prefs.edit(commit = true) {
            putLong(ACCESS_TOKEN_EXPIRES_AT_KEY, accessExpiresAt)
        }
    }

    override fun setRefreshToken(refreshToken: String?) {
        prefs.edit(commit = true) {
            putString(REFRESH_TOKEN_KEY, refreshToken)
        }
    }

    override fun setRefreshExpiresAt(refreshExpiresAt: Long) {
        prefs.edit(commit = true) {
            putLong(REFRESH_TOKEN_EXPIRES_AT_KEY, refreshExpiresAt)
        }
    }

    override fun setIdToken(idToken: String?) {
        prefs.edit(commit = true) {
            putString(ID_TOKEN_KEY, idToken)
        }
    }

    override fun update(tokenResponse: TokenResponse) {
        prefs.edit(commit = true) {
            putString(ACCESS_TOKEN_KEY, tokenResponse.accessToken)
                .putLong(
                    ACCESS_TOKEN_EXPIRES_AT_KEY,
                    System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
                )
                .putString(REFRESH_TOKEN_KEY, tokenResponse.refreshToken)
                .putLong(
                    REFRESH_TOKEN_EXPIRES_AT_KEY,
                    System.currentTimeMillis() + (tokenResponse.refreshExpiresIn * 1000)
                )
                .putString(ID_TOKEN_KEY, tokenResponse.idToken)
        }
    }

    fun clearTokens() {
        accessToken = null
        accessExpiresAt = 0
        refreshToken = null
        refreshExpiresAt = 0
        idToken = null
    }
}
