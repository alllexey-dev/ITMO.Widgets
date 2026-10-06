package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.InMemoryPreferencesDataStore
import dev.alllexey.itmowidgets.core.testing.RecordingAppNotifier
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarksScheduler
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMarkTrackingTest {

    private val tokens = FakeSessionTokenStore()
    private val settings = MarkSourcePreferences(InMemoryPreferencesDataStore())
    private val scheduler = FakeMarksScheduler()
    private val repository = FakeMarkTrackingRepository()
    private val notifier = RecordingAppNotifier()
    private val tracking = DefaultMarkTracking(settings, tokens, scheduler, repository, notifier)

    @Test
    fun `My ITMO is on and BARS is undecided by default`() = runTest {
        assertTrue(settings.getMyItmoMarksEnabled())
        assertNull(settings.getBarsMarksEnabled())
    }

    @Test
    fun `turning My ITMO off forgets its snapshot and keeps the check only for BARS`() = runTest {
        settings.setSheetMarksEnabled(false)
        tracking.setMyItmoEnabled(false)
        assertEquals(listOf(MarkSource.MY_ITMO), repository.resets)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(0, scheduler.ensureCalls)

        settings.setBarsMarksEnabled(true)
        tracking.setMyItmoEnabled(false)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(1, scheduler.cancelCalls)
        assertTrue(notifier.cancelled.isEmpty())
    }

    @Test
    fun `turning BARS off forgets its snapshot and withdraws the prompt`() = runTest {
        settings.setBarsMarksEnabled(true)
        settings.setBarsLoginPrompt(BarsLoginPrompt.SHOWN)

        tracking.setBarsEnabled(false)

        assertEquals(false, settings.getBarsMarksEnabled())
        assertEquals(listOf(MarkSource.BARS), repository.resets)
        assertEquals(BarsLoginPrompt.NONE, settings.getBarsLoginPrompt())
        assertEquals(listOf(AppNotificationChannels.MARKS to MarkDigests.PROMPT_ID), notifier.cancelled)
        assertEquals(1, scheduler.ensureCalls)
    }

    @Test
    fun `turning a source on keeps the snapshots and starts the check`() = runTest {
        tracking.setBarsEnabled(true)
        tracking.setMyItmoEnabled(true)

        assertTrue(repository.resets.isEmpty())
        assertEquals(2, scheduler.ensureCalls)
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun `sync follows the session and both switches`() = runTest {
        val bars = listOf(null, false, true)
        for (signedIn in listOf(true, false)) for (myItmo in listOf(true, false)) for (barsSwitch in bars) {
            val scheduler = FakeMarksScheduler()
            val settings = MarkSourcePreferences(InMemoryPreferencesDataStore()).apply {
                setMyItmoMarksEnabled(myItmo)
                barsSwitch?.let { setBarsMarksEnabled(it) }
                setSheetMarksEnabled(false)
            }
            val tracking = DefaultMarkTracking(settings, FakeSessionTokenStore(signedIn), scheduler, FakeMarkTrackingRepository(),
                RecordingAppNotifier())

            tracking.syncWork()

            val running = signedIn && (myItmo || barsSwitch == true)
            val case = "$signedIn/$myItmo/$barsSwitch"
            assertEquals(case, if (running) 1 else 0, scheduler.ensureCalls)
            assertEquals(case, if (running) 0 else 1, scheduler.cancelCalls)
        }
    }

    @Test
    fun `stop cancels and check now runs once`() {
        tracking.stopWork()
        tracking.checkNow()

        assertEquals(1, scheduler.cancelCalls)
        assertEquals(1, scheduler.runOnceCalls)
        assertEquals(0, scheduler.ensureCalls)
    }


    @Test
    fun `sheet marks are on by default`() = runTest {
        assertTrue(settings.getSheetMarksEnabled())
    }

    @Test
    fun `turning sheets off untracks them and keeps the check while My ITMO is on`() = runTest {
        tracking.setSheetsEnabled(false)

        assertEquals(false, settings.getSheetMarksEnabled())
        assertEquals(listOf(MarkSource.SHEETS), repository.resets)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(0, scheduler.cancelCalls)
    }

    @Test
    fun `all three off cancel the check and sheets alone keep it`() = runTest {
        settings.setMyItmoMarksEnabled(false)
        settings.setBarsMarksEnabled(false)

        tracking.setSheetsEnabled(false)
        assertEquals(1, scheduler.cancelCalls)

        tracking.setSheetsEnabled(true)
        assertEquals(1, scheduler.ensureCalls)
        assertEquals(listOf(MarkSource.SHEETS), repository.resets)
    }
}
