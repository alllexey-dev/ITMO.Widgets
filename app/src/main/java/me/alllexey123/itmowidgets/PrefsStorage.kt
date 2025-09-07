package me.alllexey123.itmowidgets

import android.content.SharedPreferences
import api.myitmo.storage.Storage
import androidx.core.content.edit

class PrefsStorage(val prefs: SharedPreferences) : Storage{

    override fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    override fun getAccessExpiresAt(): Long {
        return prefs.getLong("access_token_expires", 0)
    }

    override fun getRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }

    override fun getRefreshExpiresAt(): Long {
        return prefs.getLong("refresh_token_expires", 0)
    }

    override fun getIdToken(): String? {
        return prefs.getString("id_token", null)
    }

    override fun setAccessToken(accessToken: String?) {
        prefs.edit {
            putString("access_token", accessToken)
        }
    }

    override fun setAccessExpiresAt(accessExpiresAt: Long) {
        prefs.edit {
            putLong("access_token_expires", accessExpiresAt)
        }
    }

    override fun setRefreshToken(refreshToken: String?) {
        prefs.edit {
            putString("refresh_token", refreshToken)
        }
    }

    override fun setRefreshExpiresAt(refreshExpiresAt: Long) {
        prefs.edit {
            putLong("refresh_token_expires", refreshExpiresAt)
        }
    }

    override fun setIdToken(idToken: String?) {
        prefs.edit {
            putString("id_token", idToken)
        }
    }
}