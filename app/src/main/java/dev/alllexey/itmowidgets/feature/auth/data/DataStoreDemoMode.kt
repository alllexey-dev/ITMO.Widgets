package dev.alllexey.itmowidgets.feature.auth.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DataStoreDemoMode @Inject constructor(
    private val settings: AppSettingsStorage
) : DemoMode {

    override suspend fun isActive(): Boolean = settings.getDemoActive()

    override fun observeActive(): Flow<Boolean> = settings.observeDemoActive()
}
