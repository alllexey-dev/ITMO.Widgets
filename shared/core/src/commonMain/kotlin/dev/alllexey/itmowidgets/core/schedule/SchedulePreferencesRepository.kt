package dev.alllexey.itmowidgets.core.schedule

import kotlinx.coroutines.flow.Flow

/** Local presentation preferences shared by Schedule and its widgets; they do not grant Backend access. */
interface SchedulePreferencesRepository {

    fun observeSportAutoSignEnabled(): Flow<Boolean>
}
