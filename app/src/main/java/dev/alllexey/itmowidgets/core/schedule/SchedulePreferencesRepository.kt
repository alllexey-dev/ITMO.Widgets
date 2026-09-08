package dev.alllexey.itmowidgets.core.schedule

import kotlinx.coroutines.flow.Flow

/** Local in-app presentation preferences; they do not grant Backend access. */
interface SchedulePreferencesRepository {

    fun observeSportAutoSignEnabled(): Flow<Boolean>
}
