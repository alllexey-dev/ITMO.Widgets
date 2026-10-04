package dev.alllexey.itmowidgets.app

import android.content.ComponentName
import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun manifestDeclaresTheQrPassAndTodayShortcutsWithShortLabels() {
        val shortcuts = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_MANIFEST)
            .associateBy { it.id }
        assertEquals(setOf(AppShortcuts.QR_PASS, AppShortcuts.TODAY), shortcuts.keys)

        val expected = mapOf(
            AppShortcuts.QR_PASS to Triple(AppEntryIntents.ACTION_OPEN_QR_PASS, R.string.shortcut_qr_short, R.string.shortcut_qr_long),
            AppShortcuts.TODAY to Triple(AppEntryIntents.ACTION_OPEN_TODAY, R.string.shortcut_today_short, R.string.shortcut_today_long)
        )
        val mainActivity = ComponentName(context, MainActivity::class.java)
        expected.forEach { (id, values) ->
            val (action, shortLabel, longLabel) = values
            val shortcut = shortcuts.getValue(id)
            val intent = shortcut.intent
            assertEquals(id, action, intent.action)
            assertEquals(id, mainActivity, intent.component)
            assertEquals(id, context.getString(shortLabel), shortcut.shortLabel.toString())
            assertEquals(id, context.getString(longLabel), shortcut.longLabel.toString())
            assertTrue("$id short label", shortcut.shortLabel.length <= 10)
            assertTrue("$id long label", shortcut.longLabel!!.length <= 25)
        }
    }
}
