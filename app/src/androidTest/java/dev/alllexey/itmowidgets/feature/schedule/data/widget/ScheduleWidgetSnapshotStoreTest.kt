package dev.alllexey.itmowidgets.feature.schedule.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.core.storage.ScheduleCheckPreferences
import dev.alllexey.itmowidgets.core.storage.ServicesOptInPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.*
import dev.alllexey.itmowidgets.testing.DeviceDispatchers
import java.time.OffsetDateTime
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleWidgetSnapshotStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val preferences = MemoryPreferences()
    private val scheduleChecks = ScheduleCheckPreferences(preferences)
    private val servicesOptIn = ServicesOptInPreferences(preferences)
    private val tokens = Tokens()
    private val time = Time()

    @Test
    fun recreationKeepsExplicitPendingMarkerButFlagsAndExpiryRemoveIt() = runBlocking {
        val directories = isolatedDirectories()
        enable()
        store(directories).write(snapshot())
        assertEquals(true, store(directories).read().next.lesson?.queued)
        scheduleChecks.setScheduleSportAutoSignEnabled(false)
        assertEquals(WidgetContentKind.EMPTY, store(directories).read().next.kind)
        enable()
        servicesOptIn.setCustomServicesEnabled(false)
        assertEquals(WidgetContentKind.EMPTY, store(directories).read().next.kind)
        enable()
        time.value = time.value.plusMinutes(7)
        assertEquals(WidgetContentKind.EMPTY, store(directories).read().next.kind)
    }

    @Test
    fun sessionCleanupRejectsLateOldWorkerWriteAfterNewSessionStarts() = runBlocking {
        val directories = isolatedDirectories()
        enable()
        val store = store(directories)
        val oldGeneration = store.currentGeneration()
        store.write(snapshot())
        store.clearSessionData()
        // A new login has valid tokens again; the pre-cleanup ticket must still be invalid.
        tokens.signedIn = true
        assertFalse(store.writeIfCurrent(snapshot(), oldGeneration))
        assertEquals(WidgetContentKind.LOADING, store.read().next.kind)
        val current = store.currentGeneration()
        assertTrue(store.writeIfCurrent(snapshot(), current))
        assertEquals(true, store.read().next.lesson?.queued)
        store.clearSessionData()
        assertEquals(WidgetContentKind.LOADING, store(directories).read().next.kind)
    }

    @Test
    fun sessionCleanupInvalidatesReadSuspendedInSettingsEvenAfterNewLogin() = runBlocking {
        withTimeout(5_000) {
            val directories = isolatedDirectories()
            enable()
            val store = store(directories)
            store.write(snapshot())
            val enteredSettings = CompletableDeferred<Unit>()
            val resumeSettings = CompletableDeferred<Unit>()
            preferences.beforeRead = {
                enteredSettings.complete(Unit)
                resumeSettings.await()
            }
            val read = async { store.read() }
            enteredSettings.await()
            store.clearSessionData()
            tokens.signedIn = true
            resumeSettings.complete(Unit)
            assertEquals(WidgetContentKind.LOADING, read.await().next.kind)
        }
    }

    @Test
    fun signedOutReadNeverExposesPersistedData() = runBlocking {
        val directories = isolatedDirectories()
        enable()
        store(directories).write(snapshot())
        tokens.signedIn = false
        assertEquals(WidgetContentKind.SIGNED_OUT, store(directories).read().next.kind)
    }

    private fun isolatedDirectories(): AppDirectories = Directories(temporaryFolder.newFolder().toOkioPath())

    private fun store(directories: AppDirectories) = ScheduleWidgetSnapshotStoreImpl(
        directories, scheduleChecks, DefaultBackendGate(servicesOptIn, NoDemo), time, tokens, DeviceDispatchers
    )

    private suspend fun enable() {
        scheduleChecks.setScheduleSportAutoSignEnabled(true)
        servicesOptIn.setCustomServicesEnabled(true)
    }

    private fun snapshot(): ScheduleWidgetSnapshot {
        val official = ScheduleWidgetSnapshot(NextLessonWidgetContent(WidgetContentKind.EMPTY), DayWidgetContent(WidgetContentKind.EMPTY))
        val pending = WidgetLesson("Плавание", "11:00", "12:00", 660, 720, 11, WidgetLessonState.UPCOMING, room = "Бассейн", queued = true)
        return official.copy(
            next = NextLessonWidgetContent(WidgetContentKind.CONTENT, NextLessonMoment.FIRST, pending),
            officialFallback = official,
            pendingValidUntil = time.value.plusMinutes(7).toInstant().toString()
        )
    }

    private class Directories(override val noBackup: Path) : AppDirectories {
        override val files: Path get() = error("The snapshot lives in noBackup")
        override val cache: Path get() = error("The snapshot lives in noBackup")
    }

    private class MemoryPreferences : DataStore<Preferences> {
        private var value: Preferences = emptyPreferences()
        var beforeRead: suspend () -> Unit = {}
        override val data = flow { beforeRead(); emit(value) }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(value).also { value = it }
    }

    private class Tokens : SessionTokenStore {
        var signedIn = true
        override fun hasRefreshToken() = signedIn
        override fun getIdToken(): String? = null
        override fun replaceWithRefreshToken(refreshToken: String) = Unit
        override fun clearTokens() { signedIn = false }
    }

    private object NoDemo : DemoMode {
        override suspend fun isActive() = false
        override fun observeActive() = flowOf(false)
    }

    private class Time : AcademicTimeProvider {
        var value: OffsetDateTime = OffsetDateTime.parse("2026-09-08T10:00:00+03:00")
        override val timeZone = TimeZone.of("Europe/Moscow")
        override fun now() = value.toInstant().toKotlinInstant()
        override fun today() = value.toLocalDate().toKotlinLocalDate()
    }
}
