package dev.alllexey.itmowidgets.feature.recordbook

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
import dev.alllexey.itmowidgets.app.AndroidAppNotifier
import dev.alllexey.itmowidgets.app.MainActivity
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.from
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDebugEntryPoint
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigest
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSubjectTarget
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.work.AndroidMarksNotifier
import dev.alllexey.itmowidgets.feature.recordbook.work.MarksTestEntryPoint
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The marks notifications from synthetic digests; no check runs and nothing is stored. */
@RunWith(AndroidJUnit4::class)
class MarksNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = EntryPointAccessors.fromApplication(context, MarksTestEntryPoint::class.java).marksNotifier()
    private val appNotifier = EntryPointAccessors.fromApplication(context, NotificationDebugEntryPoint::class.java).notifier()

    @Before
    fun grantNotifications() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun removeNotifications() {
        appNotifier.cancel(CHANNEL, DIGEST)
        appNotifier.cancel(CHANNEL, PROMPT)
    }

    @Test
    fun digestNamesThreeSubjectsHidesThemOnTheLockScreenAndOpensTheRecordbook() {
        notifier.showDigest(MarkDigest(names(3), single = null), target = null)

        val shown = eventually(DIGEST)
        assertEquals(CHANNEL, shown.notification.channelId)
        assertEquals("Новые оценки", shown.title())
        assertEquals("Тестовый предмет 1, Тестовый предмет 2, Тестовый предмет 3", shown.text())
        assertEquals(Notification.VISIBILITY_PRIVATE, shown.notification.visibility)
        val public = checkNotNull(shown.notification.publicVersion) { "The lock screen needs a public version" }
        assertEquals("Новые оценки", public.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertNull(public.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(pendingIntentOf(AppEntryIntents.ACTION_OPEN_RECORDBOOK, DIGEST), shown.notification.contentIntent)
    }

    @Test
    fun fiveSubjectsNameThreeAndCountTheRest() {
        notifier.showDigest(MarkDigest(names(5), single = null), target = null)

        assertEquals("Тестовый предмет 1, Тестовый предмет 2, Тестовый предмет 3 и ещё\u00A02", eventually(DIGEST).text())
    }

    @Test
    fun oneSubjectWithAPageOpensItAndItsArgumentsSurviveTheIntent() {
        val journal = BarsJournalReference(7, "flow", "7", 2026, 1)
        listOf(journal, null).forEach { bars ->
            val target = MarkSubjectTarget(programId = 1, semester = 3, studyYear = "2026/2027", entryId = 11, bars = bars)
            val captured = CapturingNotifier()
            AndroidMarksNotifier(context, captured).showDigest(MarkDigest(names(1), single = null), target)

            val intent = AndroidAppNotifier(context).intentFor(captured.shown.single())

            assertEquals(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, intent.action)
            assertEquals(
                RecordbookSubjectArgs(11, 1, 3, "2026/2027", bars?.planId, bars?.type, bars?.identifier),
                RecordbookSubjectArgs.from(intent.extras)
            )
        }
        notifier.showDigest(MarkDigest(names(1), single = null), MarkSubjectTarget(1, 3, "2026/2027", 11, journal))
        assertEquals(pendingIntentOf(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, DIGEST), eventually(DIGEST).notification.contentIntent)
    }

    @Test
    fun barsPromptIsASecondNotificationThatOpensTheSignIn() {
        notifier.showDigest(MarkDigest(names(2), single = null), target = null)
        eventually(DIGEST)

        notifier.showBarsPrompt()

        val prompt = eventually(PROMPT)
        assertEquals("Войдите в БАРС", prompt.title())
        assertEquals("Без входа оценки БАРС не проверяются.", prompt.text())
        assertEquals(Notification.VISIBILITY_PRIVATE, prompt.notification.visibility)
        assertEquals("Войдите в БАРС", prompt.notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(pendingIntentOf(AppEntryIntents.ACTION_OPEN_BARS_LOGIN, PROMPT), prompt.notification.contentIntent)
        assertEquals(1, ours(DIGEST).size)
    }

    @Test
    fun cancellingTheDigestRemovesIt() {
        notifier.showDigest(MarkDigest(names(1), single = null), target = null)
        eventually(DIGEST)

        appNotifier.cancel(CHANNEL, DIGEST)

        TestUi.eventually(message = "Reading everything must remove the digest") { assertTrue(ours(DIGEST).isEmpty()) }
    }

    @Test
    fun channelIsDefaultImportanceAndNamedInRussian() {
        notifier.showDigest(MarkDigest(names(2), single = null), target = null)
        eventually(DIGEST)

        val channel = manager.getNotificationChannel(CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals("Оценки", channel.name.toString())
        if (InstrumentationRegistry.getArguments().getString("holdShade") == "true") holdShadeForScreenshot()
    }

    /** External adb screenshots capture SystemUI independently of instrumentation. */
    private fun holdShadeForScreenshot() {
        shell("cmd statusbar expand-notifications")
        instrumentation.sendStatus(0, Bundle().apply { putString("marks_visual", "shade_ready") })
        Thread.sleep(12_000)
        shell("cmd statusbar collapse")
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }

    /** The app's own intent for a tap; FLAG_NO_CREATE returns it only when the notification registered the same one. */
    private fun pendingIntentOf(action: String, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .setData(Uri.parse("itmowidgets-notification://$CHANNEL/$id"))
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertNotNull("The tap must open $action", pending)
        return pending
    }

    private fun eventually(id: Int): StatusBarNotification {
        var shown: StatusBarNotification? = null
        TestUi.eventually(message = "Notification $id must be shown") { shown = ours(id).single() }
        return shown!!
    }

    private fun ours(id: Int) = manager.activeNotifications.filter { it.tag == CHANNEL && it.id == id }

    private fun names(count: Int) = (1..count).map { "Тестовый предмет $it" }

    private fun StatusBarNotification.title() = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun StatusBarNotification.text() = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    private class CapturingNotifier : AppNotifier {
        val shown = mutableListOf<AppNotification>()
        override fun show(notification: AppNotification) {
            shown += notification
        }
        override fun cancel(channel: String, id: Int) = Unit
        override fun clear() = Unit
    }

    private companion object {
        const val CHANNEL = "marks"
        const val DIGEST = 1
        const val PROMPT = 2
    }
}
