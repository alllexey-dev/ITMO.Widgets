package dev.alllexey.itmowidgets.testing

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * The instrumentation runner of `:app` (SH-FIX-DL): AndroidJUnitRunner with the real ITMO and Backend hosts
 * unreachable. A signed-in UI test seeds a fake session, and a screen loading from a slow or hung my.itmo.ru animates
 * its progress for as long as the request waits, so the main thread never idles and the test fails on an unrelated
 * outage. [OfflineHosts] makes those requests fail at once; the screens show their error state and go idle.
 */
class OfflineHostsTestRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle?) {
        // Before Application.onCreate, so every OkHttpClient the app builds takes this selector.
        OfflineHosts.install()
        super.onCreate(arguments)
    }
}

/**
 * Routes requests to [ROOTS] and their subdomains through a proxy on a closed local port, so OkHttp fails them with a
 * `ConnectException` in milliseconds. Every other host keeps the system's route (localhost fixtures, Google services).
 * Only the test process installs it; no build of the app contains it.
 */
internal object OfflineHosts {

    /** The external services the app calls: MyITMO, ITMO.ID, BARS, the QR pass, ITMO.Widgets Backend (dev and prod). */
    val ROOTS = setOf("itmo.ru", "itmo.su", "widgets.alllexey.dev")

    private val closedPort = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1))

    fun install() {
        val system = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> =
                if (isOffline(uri.host)) listOf(closedPort) else system.select(uri)

            override fun connectFailed(uri: URI, address: SocketAddress, error: IOException) {
                if (!isOffline(uri.host)) system.connectFailed(uri, address, error)
            }
        })
    }

    fun isOffline(host: String?): Boolean = host != null && ROOTS.any { host == it || host.endsWith(".$it") }
}
