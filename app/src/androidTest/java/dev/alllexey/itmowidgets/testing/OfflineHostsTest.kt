package dev.alllexey.itmowidgets.testing

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The runner keeps the real ITMO and Backend hosts off the test process and leaves every other host alone. */
@RunWith(AndroidJUnit4::class)
class OfflineHostsTest {

    @Test
    fun theExternalServicesAndTheirSubdomainsAreOffline() {
        listOf("my.itmo.ru", "id.itmo.ru", "bars.itmo.ru", "qr.itmo.su", "dev.my.itmo.su", "widgets.alllexey.dev",
            "dev.widgets.alllexey.dev").forEach { assertTrue(it, OfflineHosts.isOffline(it)) }
        listOf("localhost", "127.0.0.1", "docs.google.com", "notitmo.ru", "alllexey.dev")
            .forEach { assertFalse(it, OfflineHosts.isOffline(it)) }
        assertEquals(Proxy.Type.DIRECT, ProxySelector.getDefault().select(URI("https://docs.google.com/")).single().type())
    }

    @Test
    fun aRequestToMyItmoFailsAtOnce() {
        val started = System.nanoTime()

        assertThrows(IOException::class.java) {
            OkHttpClient().newCall(Request.Builder().url("https://my.itmo.ru/api/").build()).execute()
        }

        assertTrue("failed in ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 2_000_000_000L)
    }
}
