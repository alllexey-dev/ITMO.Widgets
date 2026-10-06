package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import kotlinx.coroutines.flow.Flow

class DataStoreDemoMode(
    private val demoPreferences: DemoPreferences
) : DemoMode {

    override suspend fun isActive(): Boolean = demoPreferences.getDemoActive()

    override fun observeActive(): Flow<Boolean> = demoPreferences.observeDemoActive()
}
