package dev.alllexey.itmowidgets.feature.settings.ui

import dev.alllexey.itmowidgets.feature.settings.domain.QrTileAddResult
import org.junit.Assert.assertEquals
import org.junit.Test

class QrTileRequestTest {

    @Test
    fun `system answer codes map to the results the settings screen handles`() {
        assertEquals(QrTileAddResult.NOT_ADDED, qrTileAddResultOf(0))
        assertEquals(QrTileAddResult.ALREADY_ADDED, qrTileAddResultOf(1))
        assertEquals(QrTileAddResult.ADDED, qrTileAddResultOf(2))
        assertEquals(QrTileAddResult.IN_PROGRESS, qrTileAddResultOf(1001))
        listOf(1000, 1002, 1003, 1004, 1005).forEach { code ->
            assertEquals("code $code", QrTileAddResult.FAILED, qrTileAddResultOf(code))
        }
    }
}
