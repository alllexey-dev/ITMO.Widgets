package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSchedulePreferencesRepository(enabled: Boolean = false) : SchedulePreferencesRepository {
    val enabled = MutableStateFlow(enabled)
    override fun observeSportAutoSignEnabled() = enabled
}
