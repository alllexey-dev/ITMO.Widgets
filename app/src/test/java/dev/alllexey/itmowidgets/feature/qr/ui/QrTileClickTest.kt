package dev.alllexey.itmowidgets.feature.qr.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QrTileClickTest {

    @Test
    fun `a locked device opens the pass only after unlocking`() {
        val host = FakeHost(locked = true)

        QrTileClick.handle(host)

        assertEquals(1, host.unlockRequests.size)
        assertEquals(0, host.opened)
        host.unlockRequests.single().run()
        assertEquals(1, host.opened)
    }

    @Test
    fun `an unlocked device opens the pass at once`() {
        val host = FakeHost(locked = false)

        QrTileClick.handle(host)

        assertEquals(0, host.unlockRequests.size)
        assertEquals(1, host.opened)
    }

    @Test
    fun `Android 14 and later launch through a PendingIntent`() {
        assertEquals(QrTileLaunch.INTENT, qrTileLaunchFor(33))
        assertEquals(QrTileLaunch.INTENT, qrTileLaunchFor(26))
        assertEquals(QrTileLaunch.PENDING_INTENT, qrTileLaunchFor(34))
        assertEquals(QrTileLaunch.PENDING_INTENT, qrTileLaunchFor(36))
    }

    private class FakeHost(private val locked: Boolean) : QrTileHost {
        val unlockRequests = mutableListOf<Runnable>()
        var opened = 0

        override fun isLocked(): Boolean = locked

        override fun unlockAndRun(action: Runnable) {
            unlockRequests += action
        }

        override fun openPass() {
            opened += 1
        }
    }
}
