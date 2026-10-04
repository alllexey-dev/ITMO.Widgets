package dev.alllexey.itmowidgets.core.notification

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.core.storage.DeviceHintPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences

/** Test-only access to the actual debug graph; no receiver or exported debug endpoint. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationDebugEntryPoint {
    fun notifier(): AppNotifier
    fun tokens(): SessionTokenStore
    fun session(): SessionRepository
    fun servicesOptIn(): ServicesOptInPreferences
    fun deviceHints(): DeviceHintPreferences
    fun demoPreferences(): DemoPreferences
}
