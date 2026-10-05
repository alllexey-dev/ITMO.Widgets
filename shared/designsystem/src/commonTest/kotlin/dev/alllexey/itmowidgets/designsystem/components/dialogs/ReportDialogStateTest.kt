package dev.alllexey.itmowidgets.designsystem.components.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportDialogStateTest {
    @Test
    fun sendingNeedsAReasonAndHappensOnce() {
        assertFalse(ReportDialogState().canSend)
        assertTrue(ReportDialogState(selectedReason = 2).canSend)
        assertFalse(ReportDialogState(selectedReason = 2, sending = true).canSend)
    }

    @Test
    fun gesturesCloseTheDialogOnlyWhileNothingIsSent() {
        assertTrue(ReportDialogState().dismissibleByGesture)
        assertTrue(ReportDialogState(selectedReason = 0, error = "Нет связи").dismissibleByGesture)
        assertFalse(ReportDialogState(selectedReason = 0, sending = true).dismissibleByGesture)
    }

    @Test
    fun theCommentIsCutAtTheLimit() {
        val long = "а".repeat(ReportDialogState.COMMENT_MAX_LENGTH + 20)

        assertEquals(ReportDialogState.COMMENT_MAX_LENGTH, ReportDialogState.limitComment(long).length)
        assertEquals("коротко", ReportDialogState.limitComment("коротко"))
    }
}
