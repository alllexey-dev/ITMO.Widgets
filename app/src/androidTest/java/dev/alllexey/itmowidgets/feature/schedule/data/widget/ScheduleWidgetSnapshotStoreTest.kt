package dev.alllexey.itmowidgets.feature.schedule.data.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.services.DefaultBackendGate
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.settings.LessonStyle
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
        assertEquals(
            ScheduleWidgetPendingStatus.PREDICTED,
            store(directories).read().singleLesson.lesson?.pendingStatus
        )
        scheduleChecks.setScheduleSportAutoSignEnabled(false)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, store(directories).read().singleLesson.kind)
        enable()
        servicesOptIn.setCustomServicesEnabled(false)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, store(directories).read().singleLesson.kind)
        enable()
        time.value = time.value.plusMinutes(7)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, store(directories).read().singleLesson.kind)
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
        assertEquals(SingleLessonWidgetKind.LOADING, store.read().singleLesson.kind)
        val current = store.currentGeneration()
        assertTrue(store.writeIfCurrent(snapshot(), current))
        assertEquals(ScheduleWidgetPendingStatus.PREDICTED, store.read().singleLesson.lesson?.pendingStatus)
        store.clearSessionData()
        assertEquals(SingleLessonWidgetKind.LOADING, store(directories).read().singleLesson.kind)
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
            assertEquals(SingleLessonWidgetKind.LOADING, read.await().singleLesson.kind)
        }
    }

    @Test
    fun signedOutReadNeverExposesPersistedData() = runBlocking {
        val directories = isolatedDirectories()
        enable()
        store(directories).write(snapshot())
        tokens.signedIn = false
        assertEquals(SingleLessonWidgetKind.SIGNED_OUT, store(directories).read().singleLesson.kind)
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
        val official = ScheduleWidgetSnapshot(
            SingleLessonWidgetContent(SingleLessonWidgetKind.EMPTY_TODAY),
            listOf(ScheduleListWidgetItem(ScheduleListWidgetItemKind.EMPTY_TODAY)), LessonStyle.DOT, LessonStyle.DOT
        )
        val pending = ScheduleWidgetLesson("Плавание", "11:00", "12:00", 11, null, "Бассейн", null,
            ScheduleWidgetLessonState.UPCOMING, ScheduleWidgetPendingStatus.PREDICTED)
        return official.copy(
            singleLesson = SingleLessonWidgetContent(SingleLessonWidgetKind.LESSON, pending),
            lessonList = listOf(ScheduleListWidgetItem(ScheduleListWidgetItemKind.LESSON, pending)),
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
        override fun replaceWithTokens(tokens: SessionTokens) = Unit
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
