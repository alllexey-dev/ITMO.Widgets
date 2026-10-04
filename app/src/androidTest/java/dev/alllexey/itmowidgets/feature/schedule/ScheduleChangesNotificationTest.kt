package dev.alllexey.itmowidgets.feature.schedule

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import dev.alllexey.itmowidgets.feature.schedule.work.ScheduleChangesTestEntryPoint
import dev.alllexey.itmowidgets.testing.TestUi
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The summary notification from a synthetic digest; no check runs and nothing is stored. */
@RunWith(AndroidJUnit4::class)
class ScheduleChangesNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = EntryPointAccessors.fromApplication(context, ScheduleChangesTestEntryPoint::class.java)
        .scheduleChangeNotifier()
    private val appNotifier = EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java).notifier()

    @Before
    fun grantNotifications() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun removeNotification() {
        appNotifier.cancel(CHANNEL, 1)
    }

    @Test
    fun digestIsOneReplaceableNotificationThatOpensTheHistory() {
        notifier.show(ScheduleChangeDigest(unread = 3, first = cancelled(), audible = true))

        val shown = eventuallySingle()
        assertEquals(CHANNEL, shown.notification.channelId)
        assertEquals("Расписание изменилось: 3 пары", shown.title())
        assertEquals("Физика — отменена: вт, 8 сентября, 10:00", shown.text())
        val expected = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .setAction(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)
                .setData(Uri.parse("itmowidgets-notification://$CHANNEL/1")),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        assertNotNull("The tap must open the schedule changes", expected)
        assertEquals(expected, shown.notification.contentIntent)

        notifier.show(ScheduleChangeDigest(unread = 1, first = cancelled(), audible = false))
        TestUi.eventually(message = "The digest must replace the previous one") {
            assertEquals("Расписание изменилось: 1 пара", ours().single().title())
        }

        appNotifier.cancel(CHANNEL, 1)
        TestUi.eventually(message = "Marking read must remove the digest") { assertTrue(ours().isEmpty()) }
    }

    @Test
    fun channelIsDefaultImportanceAndNamedInRussian() {
        notifier.show(ScheduleChangeDigest(unread = 2, first = cancelled(), audible = false))
        eventuallySingle()

        val channel = manager.getNotificationChannel(CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Изменения расписания", channel.name.toString())
        if (InstrumentationRegistry.getArguments().getString("holdShade") == "true") holdShadeForScreenshot()
    }

    /** External adb screenshots capture SystemUI independently of instrumentation. */
    private fun holdShadeForScreenshot() {
        shell("cmd statusbar expand-notifications")
        instrumentation.sendStatus(0, Bundle().apply { putString("schedule_changes_visual", "shade_ready") })
        Thread.sleep(12_000)
        shell("cmd statusbar collapse")
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    private fun eventuallySingle(): StatusBarNotification {
        var shown: StatusBarNotification? = null
        TestUi.eventually(message = "The digest must be shown") { shown = ours().single() }
        return shown!!
    }

    private fun ours() = manager.activeNotifications.filter { it.tag == CHANNEL && it.id == 1 }

    private fun StatusBarNotification.title() = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun StatusBarNotification.text() = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private fun cancelled() = ScheduleChange(
        id = "synthetic", detectedAt = Instant.parse("2026-09-07T09:00:00Z"), kind = ScheduleChangeKind.CANCELLED,
        fields = emptySet(), subjectName = "Физика", typeId = 1, flowName = null,
        before = LessonSlot(
            pairId = 7, date = LocalDate.of(2026, 9, 8), start = LocalTime.of(10, 0), end = LocalTime.of(11, 30),
            room = "1506", building = null, formatId = 1, format = null, teacherIsu = null, teacherName = null
        ),
        after = null, read = false, notified = false
    )

    private companion object {
        const val CHANNEL = "schedule_changes"
    }
}
