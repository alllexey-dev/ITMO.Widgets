package dev.alllexey.itmowidgets.feature.schedule.data.widget

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.SessionTokens
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.*
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleWidgetSnapshotStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val preferences = MemoryPreferences()
    private val settings = AppSettingsStorage(preferences)
    private val tokens = Tokens()
    private val time = Time()

    @Test
    fun recreationKeepsExplicitPendingMarkerButFlagsAndExpiryRemoveIt() = runBlocking {
        val context = isolatedContext()
        enable()
        store(context).write(snapshot())
        assertEquals(ScheduleWidgetPendingStatus.PREDICTED, store(context).read().singleLesson.lesson?.pendingStatus)
        settings.setScheduleSportAutoSignEnabled(false)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, store(context).read().singleLesson.kind)
        enable()
        settings.setCustomServicesEnabled(false)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, store(context).read().singleLesson.kind)
        enable()
        time.value = time.value.plusMinutes(7)
        assertEquals(SingleLessonWidgetKind.EMPTY_TODAY, store(context).read().singleLesson.kind)
    }

    @Test
    fun sessionCleanupRejectsLateOldWorkerWriteAfterNewSessionStarts() = runBlocking {
        val context = isolatedContext()
        enable()
        val store = store(context)
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
        assertEquals(SingleLessonWidgetKind.LOADING, store(context).read().singleLesson.kind)
    }

    @Test
    fun sessionCleanupInvalidatesReadSuspendedInSettingsEvenAfterNewLogin() = runBlocking {
        withTimeout(5_000) {
            val context = isolatedContext()
            enable()
            val store = store(context)
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
        val context = isolatedContext()
        enable()
        store(context).write(snapshot())
        tokens.signedIn = false
        assertEquals(SingleLessonWidgetKind.SIGNED_OUT, store(context).read().singleLesson.kind)
    }

    private fun isolatedContext(): Context {
        val directory = temporaryFolder.newFolder()
        return object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getNoBackupFilesDir(): File = directory
        }
    }

    private fun store(context: Context) = ScheduleWidgetSnapshotStoreImpl(Gson(), context, settings, time, tokens)

    private suspend fun enable() {
        settings.setScheduleSportAutoSignEnabled(true)
        settings.setCustomServicesEnabled(true)
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

    private class Time : AcademicTimeProvider {
        var value: OffsetDateTime = OffsetDateTime.parse("2026-09-08T10:00:00+03:00")
        override val zoneId = ZoneId.of("Europe/Moscow")
        override fun now() = value
        override fun today() = value.toLocalDate()
    }
}
