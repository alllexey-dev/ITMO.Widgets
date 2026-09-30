package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarsMarksActivationTest {

    private val settings = AppSettingsStorage(InMemoryPreferencesDataStore())
    private val scheduler = FakeMarksScheduler()
    private val notifier = RecordingAppNotifier()
    private val activation = BarsMarksActivation(settings, scheduler, notifier)

    @Test
    fun `the first BARS answer turns BARS marks on and starts the check once`() = runTest {
        activation.onBarsAnswered()
        assertEquals(true, settings.getBarsMarksEnabled())
        assertEquals(1, scheduler.ensureCalls)

        activation.onBarsAnswered()
        assertEquals(true, settings.getBarsMarksEnabled())
        assertEquals(1, scheduler.ensureCalls)
        assertTrue(notifier.cancelled.isEmpty())
    }

    @Test
    fun `a switch the user turned off stays off`() = runTest {
        settings.setBarsMarksEnabled(false)

        activation.onBarsAnswered()

        assertEquals(false, settings.getBarsMarksEnabled())
        assertEquals(0, scheduler.ensureCalls)
    }

    @Test
    fun `a shown prompt is withdrawn`() = runTest {
        settings.setBarsMarksEnabled(true)
        settings.setBarsLoginPrompt(BarsLoginPrompt.SHOWN)

        activation.onBarsAnswered()

        assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
        assertEquals(listOf(AppNotificationChannels.MARKS to MarkDigests.PROMPT_ID), notifier.cancelled)
    }

    @Test
    fun `a failing preference store does not reach the BARS request`() = runTest {
        val broken = BarsMarksActivation(AppSettingsStorage(FailingDataStore()), scheduler, notifier)

        broken.onBarsAnswered()

        assertEquals(0, scheduler.ensureCalls)
        assertTrue(notifier.cancelled.isEmpty())
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            throw IOException("synthetic write failure")
    }
}
