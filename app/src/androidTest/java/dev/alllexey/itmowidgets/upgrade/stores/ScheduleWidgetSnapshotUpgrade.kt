package dev.alllexey.itmowidgets.upgrade.stores

import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.storage.AndroidAppDirectories
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.widget.ScheduleWidgetSnapshotStoreImpl
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.ScheduleWidgetSnapshot
import dev.alllexey.itmowidgets.testing.DeviceDispatchers
import dev.alllexey.itmowidgets.upgrade.Captured22
import dev.alllexey.itmowidgets.upgrade.Upgrade22Fixture
import java.io.File
import java.time.Clock
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * `no_backup/widgets/schedule_snapshot.json`: 2.2's Gson snapshot is of the lesson-list widgets the UX overhaul
 * replaced, so it reads as loading and the next update writes `formatVersion` 3; placed widgets show their content
 * again after that update.
 */
object ScheduleWidgetSnapshotUpgrade {

    fun check(fixture: Upgrade22Fixture): Unit = runBlocking {
        val store = store(fixture)

        assertEquals(ScheduleWidgetSnapshot.loading(), store.read())
        val next = ScheduleWidgetSnapshot.signedOut().copy(compactTextSize = WidgetTextSize.LARGE)
        store.write(next)
        val written = File(fixture.noBackupFilesDir, "widgets/schedule_snapshot.json").readText()
        assertTrue(written, written.startsWith("{\"formatVersion\":3,"))
        assertEquals(next, store(fixture).read())
    }

    private fun store(fixture: Upgrade22Fixture) = ScheduleWidgetSnapshotStoreImpl(
        directories = AndroidAppDirectories(fixture.context),
        scheduleChecks = ScheduleCheckPreferences(fixture.preferences),
        backend = OptedInBackend,
        timeProvider = FixedTime(fixture.clock),
        tokens = SignedInTokens,
        dispatchers = DeviceDispatchers
    )

    private object OptedInBackend : BackendGate {
        override suspend fun isConnected() = true
        override fun observeConnected(): Flow<Boolean> = flowOf(true)
        override suspend fun mayCallBackend() = true
        override suspend fun isOptedIn() = true
    }

    private class FixedTime(private val clock: Clock) : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today() = now().toLocalDateTime(timeZone).date
        override fun now() = clock.instant().toKotlinInstant()
    }

    private object SignedInTokens : SessionTokenStore {
        override fun hasRefreshToken() = true
        override fun getIdToken() = Captured22.ID_TOKEN
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun clearTokens() = Unit
    }
}
