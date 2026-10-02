package dev.alllexey.itmowidgets.core.ui

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareTextTest {
    @Test
    fun shareTextIntentWrapsPlainTextInTheSharesheet() {
        val chooser = shareTextIntent("Профиль в ITMO.Widgets", "Имя в ITMO.Widgets: https://widgets.alllexey.dev/u/1")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.target()
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("Имя в ITMO.Widgets: https://widgets.alllexey.dev/u/1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Профиль в ITMO.Widgets", send.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Suppress("DEPRECATION")
    private fun Intent.target(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        else getParcelableExtra(Intent.EXTRA_INTENT)!!
}
