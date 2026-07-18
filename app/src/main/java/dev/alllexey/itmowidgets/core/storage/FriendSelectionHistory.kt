package dev.alllexey.itmowidgets.core.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendSelectionHistory @Inject constructor(
    private val preferences: SharedPreferences
) {

    fun getRecentIsu(): List<Int> {
        return preferences.getString(RECENT_FRIENDS_KEY, null)
            ?.split(SEPARATOR)
            ?.mapNotNull(String::toIntOrNull)
            .orEmpty()
    }

    fun record(isu: Int) {
        val recent = (listOf(isu) + getRecentIsu())
            .distinct()
            .take(MAX_RECENT_FRIENDS)
        preferences.edit {
            putString(RECENT_FRIENDS_KEY, recent.joinToString(SEPARATOR))
        }
    }

    private companion object {
        const val RECENT_FRIENDS_KEY = "recent_schedule_friends"
        const val SEPARATOR = ","
        const val MAX_RECENT_FRIENDS = 5
    }
}
