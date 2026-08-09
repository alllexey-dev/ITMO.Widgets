package dev.alllexey.itmowidgets.feature.qr.work

import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrWidgetRefreshDecisionTest {

    @Test
    fun `does not interrupt reveal animation`() {
        assertNull(decideQrWidgetRefresh(QrWidgetState.REVEALING, true, true))
    }

    @Test
    fun `does not cover a visible pass after background refresh`() {
        assertNull(decideQrWidgetRefresh(QrWidgetState.VISIBLE, true, true))
    }

    @Test
    fun `does not clear a visible pass when refresh has no result`() {
        assertNull(decideQrWidgetRefresh(QrWidgetState.VISIBLE, false, true))
    }

    @Test
    fun `keeps hidden widget covered when spoiler is enabled`() {
        assertEquals(
            QrWidgetRefreshDecision(
                state = QrWidgetState.HIDDEN,
                content = QrWidgetRefreshContent.SPOILER,
                cancelAutoHide = true
            ),
            decideQrWidgetRefresh(QrWidgetState.HIDDEN, true, true)
        )
    }

    @Test
    fun `shows refreshed pass when spoiler is disabled`() {
        assertEquals(
            QrWidgetRefreshDecision(
                state = QrWidgetState.VISIBLE,
                content = QrWidgetRefreshContent.QR_CODE,
                cancelAutoHide = true
            ),
            decideQrWidgetRefresh(QrWidgetState.HIDDEN, true, false)
        )
    }

    @Test
    fun `draws spoiler instead of an empty widget when pass is missing`() {
        assertEquals(
            QrWidgetRefreshDecision(
                state = QrWidgetState.HIDDEN,
                content = QrWidgetRefreshContent.SPOILER,
                cancelAutoHide = true
            ),
            decideQrWidgetRefresh(QrWidgetState.HIDDEN, false, true)
        )
    }
}
